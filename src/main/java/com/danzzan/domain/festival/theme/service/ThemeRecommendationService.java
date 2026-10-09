package com.danzzan.domain.festival.theme.service;

import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeRecommendationCandidate;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.dto.ThemeRecommendationResponse;
import com.danzzan.domain.festival.theme.dto.ThemeRecommendationRequest;
import com.danzzan.domain.festival.theme.exception.ThemeRecommendationException;
import com.danzzan.infra.image.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThemeRecommendationService {
    private static final Logger log = LoggerFactory.getLogger(ThemeRecommendationService.class);
    private final FestivalSettingRepository settings;
    private final ImageStorage imageStorage;
    private final ThemeRecommendationClient client;
    private final ThemeRecommendationValidator validator;
    private final Environment environment;
    private final AiThemePaletteAdjuster paletteAdjuster = new AiThemePaletteAdjuster();

    public ThemeRecommendationResponse recommend(Long festivalSettingId, ThemeRecommendationRequest request) {
        var setting = settings.findById(festivalSettingId).orElseThrow(() ->
                new ThemeRecommendationException(HttpStatus.NOT_FOUND, "FESTIVAL_SETTING_NOT_FOUND",
                        "Festival setting not found."));
        String posterUrl = setting.getPosterImageUrl();
        if (posterUrl == null || posterUrl.isBlank()) throw new ThemeRecommendationException(
                HttpStatus.CONFLICT, "POSTER_NOT_FOUND", "Upload an official poster first.");
        byte[] poster = imageStorage.readPoster(festivalSettingId, posterUrl);
        ThemeRecommendationResponse result = client.recommend(poster, request.sourceColors(), request.selectedBaseColor());
        return validateCandidates(result, request.selectedBaseColor());
    }

    private ThemeRecommendationResponse validateCandidates(ThemeRecommendationResponse result, String selectedBaseColor) {
        if (result == null || result.candidates() == null || result.candidates().size() != 3) {
            throw invalid("AI_THEME_CANDIDATE_COUNT", "AI must return exactly three theme candidates.");
        }
        List<ThemeRecommendationCandidate> candidates = result.candidates();
        Set<String> expected = Set.of("balanced", "vibrant", "deep");
        Set<String> actual = new java.util.HashSet<>();
        for (ThemeRecommendationCandidate candidate : candidates) {
            if (candidate == null || candidate.id() == null || !expected.contains(candidate.id())
                    || !actual.add(candidate.id())) {
                throw invalid("AI_THEME_DUPLICATE_CANDIDATE", "AI returned duplicate or unknown theme candidates.");
            }
        }
        if (!actual.equals(expected)) throw invalid("AI_THEME_DUPLICATE_CANDIDATE", "AI returned incomplete theme candidates.");
        List<ThemeRecommendationCandidate> prioritizedCandidates = new ArrayList<>(candidates);
        prioritizedCandidates.sort(Comparator.comparingInt(candidate -> switch (candidate.id()) {
            case "balanced" -> 0;
            case "vibrant" -> 1;
            default -> 2;
        }));
        List<ThemeRecommendationCandidate> validCandidates = new ArrayList<>();
        for (ThemeRecommendationCandidate candidate : prioritizedCandidates) {
            ThemeData adjusted = paletteAdjuster.adjust(candidate.themeData(), selectedBaseColor);
            ThemeRecommendationCandidate finalCandidate = adjusted == candidate.themeData() ? candidate
                    : new ThemeRecommendationCandidate(candidate.id(), candidate.name(), candidate.description(), adjusted);
            boolean nameValid = candidate.name() != null && !candidate.name().isBlank() && candidate.name().length() <= 60;
            boolean descriptionValid = candidate.description() != null && !candidate.description().isBlank()
                    && candidate.description().length() <= 160;
            if (!nameValid && detailedLogsEnabled()) log.warn("AI theme diagnostic candidate={} rule=CANDIDATE_NAME_INVALID threshold=length 1..60",
                    candidate.id());
            if (!descriptionValid && detailedLogsEnabled()) log.warn("AI theme diagnostic candidate={} name={} rule=CANDIDATE_DESCRIPTION_INVALID threshold=length 1..160",
                    candidate.id(), candidate.name());
            List<ThemeRecommendationValidator.Failure> failures = validator.diagnose(adjusted);
            boolean valid = nameValid && descriptionValid && failures.isEmpty();
            if (detailedLogsEnabled()) log.info("AI theme validation candidate={} name={} result={} failureCount={}",
                    candidate.id(), candidate.name(), valid ? "PASS" : "FAIL",
                    failures.size() + (nameValid ? 0 : 1) + (descriptionValid ? 0 : 1));
            for (ThemeRecommendationValidator.Failure failure : failures) {
                logFailure(candidate, failure);
            }
            if (valid) validCandidates.add(finalCandidate);
        }
        if (validCandidates.isEmpty()) throw invalid("AI_THEME_VALIDATION_FAILED",
                "AI recommendation did not satisfy theme color rules. Please try again.");
        List<ThemeRecommendationCandidate> diverseCandidates = new ArrayList<>();
        for (ThemeRecommendationCandidate candidate : validCandidates) {
            boolean distinct = true;
            for (ThemeRecommendationCandidate kept : diverseCandidates) {
                if (candidate.themeData().brand().primary().equalsIgnoreCase(kept.themeData().brand().primary())) {
                    distinct = false;
                    if (detailedLogsEnabled()) log.warn("AI theme diagnostic rule=CANDIDATE_PRIMARY_DUPLICATE "
                            + "kept={} excluded={} primary={} threshold=distinctPrimaryCount>=2",
                            kept.id(), candidate.id(), candidate.themeData().brand().primary());
                }
                double distance = brandDistance(kept, candidate);
                if (distance < 30) {
                    distinct = false;
                    if (detailedLogsEnabled()) log.warn("AI theme diagnostic rule=CANDIDATE_BRAND_TOO_SIMILAR "
                            + "first={} firstName={} second={} secondName={} firstBrand={} secondBrand={} averageRgbDistance={} threshold=averageRgbDistance>=30",
                            kept.id(), kept.name(), candidate.id(), candidate.name(), kept.themeData().brand(),
                            candidate.themeData().brand(), distance);
                }
            }
            if (distinct) diverseCandidates.add(candidate);
        }
        return new ThemeRecommendationResponse(diverseCandidates);
    }

    private void logFailure(ThemeRecommendationCandidate candidate, ThemeRecommendationValidator.Failure failure) {
        if (!detailedLogsEnabled()) return;
        log.warn("AI theme diagnostic candidate={} name={} rule={} colors={} measured={} threshold={}",
                candidate.id(), candidate.name(), failure.rule(), failure.colors(), failure.measured(), failure.threshold());
    }

    private boolean detailedLogsEnabled() {
        return !environment.acceptsProfiles(Profiles.of("prod"))
                && environment.acceptsProfiles(Profiles.of("dev", "local", "local-storage"));
    }

    private double brandDistance(ThemeRecommendationCandidate a, ThemeRecommendationCandidate b) {
        List<String> first = List.of(a.themeData().brand().primary(), a.themeData().brand().secondary(),
                a.themeData().brand().accent(), a.themeData().brand().strong());
        List<String> second = List.of(b.themeData().brand().primary(), b.themeData().brand().secondary(),
                b.themeData().brand().accent(), b.themeData().brand().strong());
        double sum = 0;
        for (int i = 0; i < first.size(); i++) {
            Color x = Color.decode(first.get(i));
            Color y = Color.decode(second.get(i));
            sum += Math.sqrt(Math.pow(x.getRed() - y.getRed(), 2)
                    + Math.pow(x.getGreen() - y.getGreen(), 2)
                    + Math.pow(x.getBlue() - y.getBlue(), 2));
        }
        return sum / first.size();
    }

    private ThemeRecommendationException invalid(String code, String message) {
        return new ThemeRecommendationException(HttpStatus.BAD_GATEWAY, code, message);
    }
}

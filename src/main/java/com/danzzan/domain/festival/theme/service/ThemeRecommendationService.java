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
            if (!valid && detailedLogsEnabled()) {
                boolean categoryUnresolved = failures.stream().anyMatch(f -> f.rule().equals("CATEGORY_DISTANCE_TOO_SMALL"));
                if (categoryUnresolved && adjusted != null && adjusted.category() != null && candidate.themeData() != null
                        && candidate.themeData().category() != null) {
                    log.warn("AI theme exclusion candidate={} stage=PALETTE_ADJUSTMENT reason=CATEGORY_DISTANCE_UNRESOLVED",
                            candidate.id());
                }
                log.warn("AI theme exclusion candidate={} stage=CANDIDATE_VALIDATION failureCount={}", candidate.id(),
                        failures.size() + (nameValid ? 0 : 1) + (descriptionValid ? 0 : 1));
            }
            if (valid) validCandidates.add(finalCandidate);
        }
        if (validCandidates.isEmpty()) throw invalid("AI_THEME_VALIDATION_FAILED",
                "AI recommendation did not satisfy theme color rules. Please try again.");
        List<ThemeRecommendationCandidate> diverseCandidates = new ArrayList<>();
        ThemeRecommendationCandidate original = validCandidates.stream()
                .filter(candidate -> candidate.id().equals("balanced")).findFirst().orElse(null);
        for (ThemeRecommendationCandidate candidate : validCandidates) {
            if (candidate == original) {
                diverseCandidates.add(candidate);
                continue;
            }
            if (original == null) { // No reference palette: retain the existing partial-success behavior.
                if (diverseCandidates.stream().noneMatch(kept -> kept.themeData().brand().equals(candidate.themeData().brand()))) {
                    diverseCandidates.add(candidate);
                } else if (detailedLogsEnabled()) {
                    log.warn("AI theme exclusion candidate={} stage=DIVERSITY rule=CANDIDATE_BRAND_DUPLICATE",
                            candidate.id());
                }
                continue;
            }
            double primaryDelta = labLightness(candidate.themeData().brand().primary())
                    - labLightness(original.themeData().brand().primary());
            double strongDelta = labLightness(candidate.themeData().brand().strong())
                    - labLightness(original.themeData().brand().strong());
            double direction = candidate.id().equals("vibrant") ? 1 : -1;
            double primaryShift = direction * primaryDelta;
            double strongShift = direction * strongDelta;
            // A modest visible shift in the main CTA color or strong color is enough; neither may strongly reverse.
            double combinedShift = .7 * primaryShift + .3 * strongShift;
            if (combinedShift >= 2.5 && Math.max(primaryShift, strongShift) >= 4
                    && Math.min(primaryShift, strongShift) > -4) {
                diverseCandidates.add(candidate);
            } else if (detailedLogsEnabled()) {
                String rule = combinedShift < 0 || Math.min(primaryShift, strongShift) <= -4
                        ? "VARIATION_WRONG_DIRECTION" : "CANDIDATE_VARIATION_TOO_SUBTLE";
                log.warn("AI theme exclusion candidate={} stage={} rule={} primaryDeltaL={} strongDeltaL={} "
                                + "directionalScore={} threshold=score>=2.5,maxDirectionalShift>=4,reverseShift>-4",
                        candidate.id(), rule.equals("VARIATION_WRONG_DIRECTION") ? "VARIATION_DIRECTION" : "DIVERSITY",
                        rule, primaryDelta, strongDelta, combinedShift);
            }
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

    private double labLightness(String hex) {
        Color color = Color.decode(hex);
        double red = linearize(color.getRed() / 255.0);
        double green = linearize(color.getGreen() / 255.0);
        double blue = linearize(color.getBlue() / 255.0);
        double y = .2126 * red + .7152 * green + .0722 * blue;
        double f = y > 216.0 / 24389 ? Math.cbrt(y) : y / (24389.0 / 27) + 16.0 / 116;
        return 116 * f - 16;
    }

    private double linearize(double channel) {
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }

    private ThemeRecommendationException invalid(String code, String message) {
        return new ThemeRecommendationException(HttpStatus.BAD_GATEWAY, code, message);
    }
}

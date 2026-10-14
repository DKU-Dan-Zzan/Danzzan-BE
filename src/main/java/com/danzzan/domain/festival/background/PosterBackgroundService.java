package com.danzzan.domain.festival.background;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.infra.image.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PosterBackgroundService {
    private final FestivalSettingRepository settings;
    private final ImageStorage storage;
    private final OpenAiPosterFocusClient ai;
    private final PosterBackgroundRenderer renderer;

    public record Recommendation(String target, String mimeType, String imageBase64, int width, int height,
                                 double focusX, double focusY, String reason, String sourcePosterUrl) {}

    /** Uses only the saved singleton poster; the optional URL is a stale-preview guard, never a fetch target. */
    public Recommendation recommend(String target) {
        return recommend(target, null);
    }

    public Recommendation recommend(String target, String expectedSourcePosterUrl) {
        var size = PosterBackgroundRenderer.Target.parse(target);
        var setting = settings.findById(FestivalSetting.SINGLETON_ID).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "축제 설정이 없습니다."));
        String sourcePosterUrl = setting.getPosterImageUrl();
        if (expectedSourcePosterUrl != null && !expectedSourcePosterUrl.equals(sourcePosterUrl))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "공식 포스터가 변경되었습니다. 다시 미리보기해 주세요.");
        if (sourcePosterUrl == null || sourcePosterUrl.isBlank())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "공식 포스터를 먼저 저장해 주세요.");
        // Both storage implementations validate the URL belongs to this festival's poster directory.
        byte[] poster = storage.readPoster(setting.getId(), sourcePosterUrl);
        var image = renderer.read(poster);
        var focus = ai.recommend(poster, target);
        byte[] jpeg = renderer.render(image, size, focus.x(), focus.y());
        return new Recommendation(target, "image/jpeg", Base64.getEncoder().encodeToString(jpeg),
                size.width() * 2, size.height() * 2, focus.x(), focus.y(), focus.reason(), sourcePosterUrl);
    }
}

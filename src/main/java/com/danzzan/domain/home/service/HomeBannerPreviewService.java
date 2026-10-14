package com.danzzan.domain.home.service;

import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.home.model.dto.HomeBannerPreviewRequest;
import com.danzzan.infra.image.ImageStorage;
import com.danzzan.infra.image.json.ImageUrlSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HomeBannerPreviewService {
    private final FestivalSettingRepository settings;
    private final ImageStorage storage;
    private final HomeBannerRenderer renderer;

    public record Preview(String mimeType, String imageBase64, int width, int height,
                          @JsonSerialize(using = ImageUrlSerializer.class) String sourcePosterUrl) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Preview preview(Long festivalId, HomeBannerPreviewRequest request) {
        if (!settings.existsById(festivalId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Festival setting not found");
        String source = settings.findPosterImageUrlById(festivalId).orElse(null);
        if (!Objects.equals(source, request.sourcePosterUrl()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "공식 포스터가 변경되었습니다. 다시 미리보기해 주세요.");
        byte[] image = renderer.render(storage.readPoster(festivalId, source), request.layout(), request.focusX(), request.focusY());
        return new Preview("image/jpeg", Base64.getEncoder().encodeToString(image),
                HomeBannerRenderer.WIDTH, HomeBannerRenderer.HEIGHT, source);
    }
}

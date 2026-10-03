package com.danzzan.domain.festival.service;

import com.danzzan.domain.festival.dto.response.PosterColorResponse;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.infra.s3.S3PosterImageReader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FestivalPosterColorService {
    private final FestivalSettingRepository settings;
    private final S3PosterImageReader reader;
    private final PosterColorExtractor extractor;

    public PosterColorResponse extract(Long festivalSettingId) {
        var setting = settings.findById(festivalSettingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Festival setting not found"));
        String posterUrl = setting.getPosterImageUrl();
        if (posterUrl == null || posterUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload an official poster first");
        }
        return extractor.extract(reader.read(festivalSettingId, posterUrl));
    }
}

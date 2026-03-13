package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.PubSummaryResponse;
import com.danzzan.domain.boothmap.model.dto.PubDetailResponse;
import com.danzzan.domain.boothmap.model.entity.Pub;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.model.entity.PubImage;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.domain.boothmap.repository.PubImageRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PubService {
    private final PubRepository pubRepository;
    private final PubImageRepository pubImageRepository;

    public List<PubSummaryResponse> getPubs() {
        List<Pub> pubs = pubRepository.findAllWithCollege();

        return pubs.stream()
            .map(pub -> {
                PubImage mainImage = pubImageRepository.findByPubIdAndIsMainTrue(pub.getId()).orElse(null);

                return new PubSummaryResponse(
                    pub.getId(),
                    pub.getName(),
                    pub.getIntro(),
                    pub.getDepartment(),
                    pub.getCollege().getName(),
                    mainImage != null ? mainImage.getImageUrl() : null
                );
            })
            .toList();
    }

    public PubDetailResponse getPubDetail(Long pubId) {

        Pub pub = pubRepository.findByIdWithCollege(pubId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주점을 찾을 수 없습니다. id=" + pubId));

        List<String> imageUrls = pubImageRepository.findByPubId(pubId)
                .stream()
                .map(image -> image.getImageUrl())
                .toList();

        return new PubDetailResponse(
                pub.getId(),
                pub.getName(),
                pub.getIntro(),
                pub.getDescription(),
                pub.getDepartment(),
                pub.getCollege().getName(),
                pub.getInstagram(),
                imageUrls
        );
    }
}
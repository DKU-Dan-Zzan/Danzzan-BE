package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.PubSummaryResponse;
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
        List<Pub> pubs = pubRepository.findAll();

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
}
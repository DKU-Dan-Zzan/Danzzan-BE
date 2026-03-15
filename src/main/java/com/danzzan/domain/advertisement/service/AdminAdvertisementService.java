package com.danzzan.domain.advertisement.service;

import com.danzzan.domain.advertisement.dto.request.CreateAdvertisementRequest;
import com.danzzan.domain.advertisement.dto.request.SetActiveRequest;
import com.danzzan.domain.advertisement.dto.request.UpdateAdvertisementRequest;
import com.danzzan.domain.advertisement.dto.response.AdvertisementResponse;
import com.danzzan.domain.advertisement.model.entity.Advertisement;
import com.danzzan.domain.advertisement.repository.AdvertisementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAdvertisementService {

    private final AdvertisementRepository advertisementRepository;

    @Transactional
    public AdvertisementResponse create(CreateAdvertisementRequest request) {
        Advertisement ad = Advertisement.create(
                request.getTitle(),
                request.getImageUrl(),
                request.getLinkUrl(),
                request.getPlacement(),
                request.getStartDate(),
                request.getEndDate(),
                request.getIsActive(),
                request.getPriority()
        );
        return AdvertisementResponse.from(advertisementRepository.save(ad));
    }

    @Transactional(readOnly = true)
    public Page<AdvertisementResponse> findAll(Pageable pageable) {
        return advertisementRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AdvertisementResponse::from);
    }

    @Transactional(readOnly = true)
    public AdvertisementResponse findById(Long id) {
        Advertisement ad = advertisementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("광고를 찾을 수 없습니다. id=" + id));
        return AdvertisementResponse.from(ad);
    }

    @Transactional
    public AdvertisementResponse update(Long id, UpdateAdvertisementRequest request) {
        Advertisement ad = advertisementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("광고를 찾을 수 없습니다. id=" + id));
        ad.setTitle(request.getTitle());
        ad.setImageUrl(request.getImageUrl());
        ad.setLinkUrl(request.getLinkUrl());
        ad.setPlacement(request.getPlacement());
        ad.setStartDate(request.getStartDate());
        ad.setEndDate(request.getEndDate());
        ad.setIsActive(request.getIsActive() != null ? request.getIsActive() : ad.getIsActive());
        ad.setPriority(request.getPriority());
        return AdvertisementResponse.from(advertisementRepository.save(ad));
    }

    @Transactional
    public void delete(Long id) {
        if (!advertisementRepository.existsById(id)) {
            throw new IllegalArgumentException("광고를 찾을 수 없습니다. id=" + id);
        }
        advertisementRepository.deleteById(id);
    }

    @Transactional
    public AdvertisementResponse setActive(Long id, SetActiveRequest request) {
        Advertisement ad = advertisementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("광고를 찾을 수 없습니다. id=" + id));
        ad.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        return AdvertisementResponse.from(advertisementRepository.save(ad));
    }
}

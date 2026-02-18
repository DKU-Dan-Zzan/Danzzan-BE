package com.danzzan.festival.domain.home.service;

import com.danzzan.festival.domain.home.model.dto.HomeImageDto;
import com.danzzan.festival.domain.home.model.entity.HomeImage;
import com.danzzan.festival.domain.home.repository.HomeImageRepository;

import com.danzzan.festival.domain.home.model.dto.EmergencyNoticeDto;
import com.danzzan.festival.domain.home.model.entity.EmergencyNotice;
import com.danzzan.festival.domain.home.repository.EmergencyNoticeRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {
    private final HomeImageRepository homeImageRepository;
    private final EmergencyNoticeRepository emergencyNoticeRepository;

    public List<HomeImageDto> getHomeImages() {
        return homeImageRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(image -> new HomeImageDto(
                    image.getId(),
                    image.getImageUrl()
                ))
                .toList();
    }

    public EmergencyNoticeDto getLatestEmergencyNotice() {
        return emergencyNoticeRepository.findTopByOrderByCreatedAtDesc()
                .map(notice -> new EmergencyNoticeDto(
                    notice.getId(),
                    notice.getContent(),
                    notice.getCreatedAt()
                ))
                .orElse(null);
    }
}
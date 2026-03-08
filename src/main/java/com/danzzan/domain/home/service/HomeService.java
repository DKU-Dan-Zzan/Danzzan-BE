package com.danzzan.domain.home.service;

import com.danzzan.domain.home.model.dto.HomeImageDto;
import com.danzzan.domain.home.repository.HomeImageRepository;

import com.danzzan.domain.home.model.dto.LineupImageDto;
import com.danzzan.domain.home.repository.LineupImageRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {
    private final HomeImageRepository homeImageRepository;
    private final LineupImageRepository lineupImageRepository;

    public List<HomeImageDto> getHomeImages() {
        return homeImageRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(image -> new HomeImageDto(
                    image.getId(),
                    image.getImageUrl()
                ))
                .toList();
    }

    public List<LineupImageDto> getLineupImages() {
        return lineupImageRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(image -> new LineupImageDto(
                    image.getId(),
                    image.getImageUrl()
                ))
                .toList();
    }
}

package com.danzzan.festival.domain.home.service;

import com.danzzan.festival.domain.home.model.dto.HomeImageDto;
import com.danzzan.festival.domain.home.model.entity.HomeImage;
import com.danzzan.festival.domain.home.repository.HomeImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeService {
    private final HomeImageRepository homeImageRepository;

    public List<HomeImageDto> getHomeImages() {
        return homeImageRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(image -> new HomeImageDto(
                    image.getId(),
                    image.getImageUrl()
                ))
                .toList();
    }
}
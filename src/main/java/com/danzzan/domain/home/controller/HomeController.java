package com.danzzan.domain.home.controller;

import com.danzzan.domain.home.model.dto.HomeImageDto;
import com.danzzan.domain.home.model.dto.LineupImageDto;
import com.danzzan.domain.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/home")
public class HomeController {
    private final HomeService homeService;

    @GetMapping("/images")
    public List<HomeImageDto> getHomeImages() {
        return homeService.getHomeImages();
    }

    @GetMapping("/lineup-images")
    public List<LineupImageDto> getLineupImages() {
        return homeService.getLineupImages();
    }
}

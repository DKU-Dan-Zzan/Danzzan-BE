package com.danzzan.domain.boothmap.controller;

import com.danzzan.domain.boothmap.model.dto.BoothMapResponse;
import com.danzzan.domain.boothmap.service.BoothMapService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/map")
public class BoothMapController {
    private final BoothMapService boothMapService;

    @GetMapping("/booth-map")
    public BoothMapResponse getBoothMap() {
        return boothMapService.getBoothMap();
    }
}
package com.danzzan.domain.boothmap.controller;

import com.danzzan.domain.boothmap.model.dto.BoothMapResponse;
import com.danzzan.domain.boothmap.service.BoothMapService;

import com.danzzan.domain.boothmap.model.dto.BoothSummaryResponse;
import com.danzzan.domain.boothmap.service.BoothService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/map")
public class MapController {
    private final BoothMapService boothMapService;
    private final BoothService boothService;

    @GetMapping("/booth-map")
    public BoothMapResponse getBoothMap() {
        return boothMapService.getBoothMap();
    }

    @GetMapping("/booths/{boothId}")
    public BoothSummaryResponse getBoothSummary(@PathVariable Long boothId) {
        return boothService.getBoothSummary(boothId);
    }
}
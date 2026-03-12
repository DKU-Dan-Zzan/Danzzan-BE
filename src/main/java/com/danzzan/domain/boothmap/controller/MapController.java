package com.danzzan.domain.boothmap.controller;

import com.danzzan.domain.boothmap.model.dto.BoothMapResponse;
import com.danzzan.domain.boothmap.service.BoothMapService;

import com.danzzan.domain.boothmap.model.dto.BoothSummaryResponse;
import com.danzzan.domain.boothmap.service.BoothService;

import com.danzzan.domain.boothmap.model.dto.PubSummaryResponse;
import com.danzzan.domain.boothmap.service.PubService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/map")
public class MapController {
    private final BoothMapService boothMapService;
    private final BoothService boothService;
    private final PubService pubService;

    @GetMapping("/booth-map")
    public BoothMapResponse getBoothMap() {
        return boothMapService.getBoothMap();
    }

    @GetMapping("/booths/{boothId}")
    public BoothSummaryResponse getBoothSummary(@PathVariable Long boothId) {
        return boothService.getBoothSummary(boothId);
    }

    @GetMapping("/pubs")
    public List<PubSummaryResponse> getPubs() {
        return pubService.getPubs();
    }
}
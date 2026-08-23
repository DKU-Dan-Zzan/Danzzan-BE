package com.danzzan.domain.admin.controller;

import com.danzzan.domain.admin.service.TranslationBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/translation")
@RequiredArgsConstructor
public class AdminTranslationBackfillController {

    private final TranslationBackfillService backfillService;

    @PostMapping("/backfill")
    public Map<String, Integer> backfill() {
        return Map.of("filled", backfillService.backfillAll());
    }
}

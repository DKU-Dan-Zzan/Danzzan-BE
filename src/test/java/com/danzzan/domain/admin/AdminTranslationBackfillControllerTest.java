package com.danzzan.domain.admin;

import com.danzzan.domain.admin.controller.AdminTranslationBackfillController;
import com.danzzan.domain.admin.service.TranslationBackfillService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTranslationBackfillControllerTest {

    @Mock
    private TranslationBackfillService backfillService;

    @InjectMocks
    private AdminTranslationBackfillController controller;

    @Test
    void 보정_실행_결과_건수를_반환한다() {
        when(backfillService.backfillAll()).thenReturn(7);

        Map<String, Integer> response = controller.backfill();

        assertEquals(7, response.get("filled"));
    }
}

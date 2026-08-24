package com.danzzan.infra.translation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final TranslationClient translationClient;
    private final GlossarySyncService glossarySyncService;

    /**
     * 단일 문자열을 번역한다. 실패하면 null을 반환하며 예외를 던지지 않는다.
     */
    public String translate(String text) {
        List<String> result = translateAll(List.of(text == null ? "" : text));
        return result.get(0);
    }

    /**
     * 목록을 번역한다. 반환 목록의 크기는 입력과 항상 같다.
     * 비어 있는 입력과 번역 실패는 모두 해당 위치에 null로 표시된다.
     */
    public List<String> translateAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<Integer> targetIndexes = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text != null && !text.isBlank()) {
                targetIndexes.add(i);
                targets.add(text);
            }
        }

        List<String> result = new ArrayList<>(Collections.nCopies(texts.size(), null));
        if (targets.isEmpty()) {
            return result;
        }

        try {
            List<String> translated =
                    translationClient.translate(targets, glossarySyncService.getGlossaryId());
            for (int i = 0; i < targetIndexes.size(); i++) {
                result.set(targetIndexes.get(i), translated.get(i));
            }
        } catch (Exception e) {
            log.warn("번역에 실패했습니다. 영문을 비워둔 채로 진행합니다. 건수={}", targets.size(), e);
        }
        return result;
    }
}

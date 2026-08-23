package com.danzzan.infra.translation;

import java.util.List;

public interface TranslationClient {

    /**
     * 한국어 텍스트를 영어로 번역한다.
     *
     * @param texts      번역할 문자열 목록
     * @param glossaryId 적용할 용어집 ID. null이면 용어집 없이 번역한다.
     * @return 입력과 동일한 순서의 번역문 목록
     * @throws TranslationUnavailableException 번역을 수행할 수 없을 때
     */
    List<String> translate(List<String> texts, String glossaryId);
}

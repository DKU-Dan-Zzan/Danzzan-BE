package com.danzzan.infra.translation;

/**
 * 번역 가능한 필드 하나(예: 공지 제목, 부스 이름 등)의 "최종적으로 저장될 영문 값"을
 * 결정하는 순수 함수 모음.
 *
 * <p>{@code NoticeService.create}/{@code update}에서 세 번의 리뷰에 걸쳐 실제 버그가
 * 발견된 로직을 뽑아낸 것이다. 버그는 항상 다음 세 단계의 <b>순서</b>에서 비롯됐다.</p>
 *
 * <ol>
 *   <li><b>지우기(clear)</b> — 관리자가 이번 요청에서 이 필드의 영문 칸을 비워뒀고
 *       (= 자동번역을 원한다는 뜻), 그 필드의 한국어가 실제로 바뀌었다면, 남아있는
 *       예전 영문 값은 낡은 정보이므로 지운 것으로 취급한다.</li>
 *   <li><b>자동 채움(auto-fill)</b> — 지우기 이후에도 값이 비어 있을 때만 자동번역
 *       결과로 채운다. ({@code Notice.applyTranslation}의 "필드가 null일 때만 채운다"는
 *       가드와 동일한 효과다.)</li>
 *   <li><b>수동 값 덮어쓰기(overlay)</b> — 관리자가 실제로 입력한 영문이 있다면, 한국어
 *       변경 여부와 무관하게 그 값이 최종적으로 이긴다. 비워둔 필드에 대해서는
 *       이 단계가 자기 자신에 대한 대입이 되어 아무 효과가 없다.</li>
 * </ol>
 *
 * <p>이 세 단계는 서로 국소적이지 않은 사실 세 가지에 의존한다: 지우기 조건이 항상
 * "뭔가 바뀌었다" 게이트의 부분집합이라는 점, 2단계의 null-허용 가드, 그리고 3단계의
 * 자기 대입이 빈 필드에서는 아무 일도 하지 않는다는 점. 이 클래스는 그 세 가지를 한
 * 곳에 모아 진리표로 검증할 수 있게 한다.</p>
 */
public final class FieldTranslationDecision {

    private FieldTranslationDecision() {
    }

    /**
     * 값이 null도 아니고 공백뿐도 아닌지, 즉 "실제로 채워져 있는지"를 판단한다.
     * 관리자가 영문 칸을 채웠는지, 검색 키워드가 주어졌는지 등 "텍스트가 있는가"를
     * 묻는 모든 자리에서 재사용하는 단일 판정이다.
     */
    public static boolean isSupplied(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 번역 가능한 필드 하나에 대해, 최종적으로 저장돼야 할 영문 값을 계산한다.
     *
     * @param koreanChanged   이 필드의 한국어 원문이 이번 요청으로 바뀌었는지 여부.
     *                        생성 시에는 항상 {@code true}다.
     * @param suppliedEnglish 관리자가 이번 요청에서 이 필드의 영문 칸에 직접 입력한 값.
     *                        비워뒀다면(=자동번역을 원한다면) null 또는 공백일 수 있다.
     * @param storedEnglish   엔티티에 현재 저장돼 있는 영문 값. 생성 시에는 항상 null이다.
     * @param autoEnglish     번역기가 돌려준 자동번역 결과. 번역에 실패했다면 null일 수 있다.
     * @return 이 필드가 최종적으로 가져야 할 영문 값. 결과가 null이면 "아무 것도 쓸
     *         필요가 없다"는 뜻이며 — 기존 값이 그대로 유지되거나(변경 없음), 방금
     *         명시적으로 비워졌거나(지우기 이후 자동번역도 실패) 둘 중 하나다. 두 경우
     *         모두 호출부는 반환값을 그대로 세터에 넘겨도 안전하다: 같은 값을 다시 쓰는
     *         것과 진짜로 비우는 것 모두 결과적으로 올바른 최종 상태를 만든다.
     */
    public static String decideEnglish(
            boolean koreanChanged,
            String suppliedEnglish,
            String storedEnglish,
            String autoEnglish
    ) {
        boolean supplied = isSupplied(suppliedEnglish);

        String value = storedEnglish;

        // 1) 지우기: 이 필드를 비워뒀고 한국어가 바뀌었다면 낡은 값을 지운 것으로 본다.
        if (!supplied && koreanChanged) {
            value = null;
        }

        // 2) 자동 채움: 지우기 이후에도 비어 있을 때만 자동번역 결과로 채운다.
        if (value == null && autoEnglish != null) {
            value = autoEnglish;
        }

        // 3) 수동 값 덮어쓰기: 관리자가 실제로 입력했다면 그 값이 항상 이긴다.
        if (supplied) {
            value = suppliedEnglish;
        }

        return value;
    }
}

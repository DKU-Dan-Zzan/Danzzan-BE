package com.danzzan.domain.advertisement.model.entity;

/**
 * 광고 노출 위치.
 * - 페이지별: HOME, BOOTH_LIST, MAP, TICKET
 * - GLOBAL: 전체 앱에서 랜덤 노출 (특정 placement에 광고가 없을 때 fallback으로 사용)
 */
public enum AdvertisementPlacement {
    HOME,
    BOOTH_LIST,
    MAP,
    TICKET,
    GLOBAL
}

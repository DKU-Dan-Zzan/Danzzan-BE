package com.danzzan.domain.boothmap.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class BoothSubTypeTest {

    @Test
    @DisplayName("FACILITY 이름에 화장실이 포함되면 TOILET을 반환한다")
    void resolveToiletSubType() {
        Booth booth = new Booth();
        ReflectionTestUtils.setField(booth, "type", BoothType.FACILITY);
        ReflectionTestUtils.setField(booth, "name", "학생회관 화장실");

        assertThat(BoothSubType.resolve(booth)).isEqualTo(BoothSubType.TOILET);
    }

    @Test
    @DisplayName("FACILITY 이름에 흡연구역이 포함되면 SMOKING_AREA를 반환한다")
    void resolveSmokingAreaSubType() {
        Booth booth = new Booth();
        ReflectionTestUtils.setField(booth, "type", BoothType.FACILITY);
        ReflectionTestUtils.setField(booth, "name", "중앙광장 흡연구역");

        assertThat(BoothSubType.resolve(booth)).isEqualTo(BoothSubType.SMOKING_AREA);
    }

    @Test
    @DisplayName("FACILITY가 아니면 subType은 null이다")
    void resolveNullWhenNotFacility() {
        Booth booth = new Booth();
        ReflectionTestUtils.setField(booth, "type", BoothType.EXPERIENCE);
        ReflectionTestUtils.setField(booth, "name", "심폐소생술 체험");

        assertThat(BoothSubType.resolve(booth)).isNull();
    }
}

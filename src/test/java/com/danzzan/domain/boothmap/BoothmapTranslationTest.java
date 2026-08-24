package com.danzzan.domain.boothmap;

import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothType;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.model.entity.Pub;
import com.danzzan.domain.timetable.model.entity.Artist;
import com.danzzan.domain.timetable.model.entity.Performance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 부스·주점·단과대·아티스트·공연 엔티티의 영문 번역 필드 보호 규칙을 검증한다.
 *
 * 보호는 엔티티가 아니라 필드 단위다:
 * - enIsManual == false: 기계번역이 무엇이든 덮어쓸 수 있다.
 * - enIsManual == true: 기계번역은 현재 값이 null인 필드만 채울 수 있다.
 *   사람이 실제로 쓴 값(null이 아닌 값)은 덮어쓰지 않는다.
 */
class BoothmapTranslationTest {

    // ---------- Booth ----------

    @Test
    void 부스_자동번역이_두_필드를_모두_채운다() {
        Booth booth = new Booth("체험 부스", BoothType.EXPERIENCE, "설명", null, null, null);

        booth.applyTranslation("Experience Booth", "Description");

        assertEquals("Experience Booth", booth.getNameEn());
        assertEquals("Description", booth.getDescriptionEn());
    }

    @Test
    void 부스_수동번역_이후에는_자동번역이_이름을_덮어쓰지_않는다() {
        Booth booth = new Booth("체험 부스", BoothType.EXPERIENCE, "설명", null, null, null);
        booth.applyManualTranslation("Manual Name", "Manual Description");

        booth.applyTranslation("Machine Name", "Machine Description");

        assertEquals("Manual Name", booth.getNameEn());
        assertEquals("Manual Description", booth.getDescriptionEn());
        assertTrue(booth.isEnIsManual());
    }

    @Test
    void 부스_수동_플래그가_켜져도_비어있는_설명은_자동번역으로_채워진다() {
        Booth booth = new Booth("체험 부스", BoothType.EXPERIENCE, "설명", null, null, null);
        booth.applyManualTranslation("Manual Name", null);

        booth.applyTranslation("Machine Name", "Machine Description");

        assertEquals("Manual Name", booth.getNameEn());
        assertEquals("Machine Description", booth.getDescriptionEn());
    }

    // ---------- Pub ----------

    @Test
    void 주점_자동번역이_네_필드를_모두_채운다() {
        Pub pub = new Pub(null, "기계공학과", "부스 이름", "소개", "설명", null);

        pub.applyTranslation("Engineering Pub", "Come join us",
                "Student-run pub", "Mechanical Engineering");

        assertEquals("Engineering Pub", pub.getNameEn());
        assertEquals("Come join us", pub.getIntroEn());
        assertEquals("Student-run pub", pub.getDescriptionEn());
        assertEquals("Mechanical Engineering", pub.getDepartmentEn());
    }

    @Test
    void 주점_수동번역_이후에는_자동번역이_덮어쓰지_않는다() {
        Pub pub = new Pub(null, "기계공학과", "부스 이름", "소개", "설명", null);
        pub.applyManualTranslation("Manual", "Manual", "Manual", "Manual");

        pub.applyTranslation("Machine", "Machine", "Machine", "Machine");

        assertEquals("Manual", pub.getNameEn());
        assertTrue(pub.isEnIsManual());
    }

    @Test
    void 주점_수동_플래그가_켜져도_비어있는_필드는_자동번역으로_채워진다() {
        Pub pub = new Pub(null, "기계공학과", "부스 이름", "소개", "설명", null);
        pub.applyManualTranslation("Manual Name", null, null, null);

        pub.applyTranslation("Machine Name", "Machine Intro", "Machine Description", "Machine Department");

        assertEquals("Manual Name", pub.getNameEn());
        assertEquals("Machine Intro", pub.getIntroEn());
        assertEquals("Machine Description", pub.getDescriptionEn());
        assertEquals("Machine Department", pub.getDepartmentEn());
    }

    // ---------- College ----------

    @Test
    void 단과대_수동번역_이후에는_자동번역이_덮어쓰지_않는다() {
        College college = BeanUtils.instantiateClass(College.class);
        college.applyManualTranslation("Manual College");

        college.applyTranslation("Machine College");

        assertEquals("Manual College", college.getNameEn());
        assertTrue(college.isEnIsManual());
    }

    @Test
    void 단과대_수동_플래그가_켜져도_비어있는_이름은_자동번역으로_채워진다() {
        College college = BeanUtils.instantiateClass(College.class);
        college.applyManualTranslation(null);

        college.applyTranslation("Machine College");

        assertEquals("Machine College", college.getNameEn());
        assertTrue(college.isEnIsManual());
    }

    // ---------- Artist ----------

    @Test
    void 아티스트_수동번역_이후에는_자동번역이_덮어쓰지_않는다() {
        Artist artist = Artist.create("아티스트", "소개", null);
        artist.applyManualTranslation("Manual Name", "Manual Description");

        artist.applyTranslation("Machine Name", "Machine Description");

        assertEquals("Manual Name", artist.getNameEn());
        assertEquals("Manual Description", artist.getDescriptionEn());
        assertTrue(artist.isEnIsManual());
    }

    @Test
    void 아티스트_수동_플래그가_켜져도_비어있는_소개는_자동번역으로_채워진다() {
        Artist artist = Artist.create("아티스트", "소개", null);
        artist.applyManualTranslation("Manual Name", null);

        artist.applyTranslation("Machine Name", "Machine Description");

        assertEquals("Manual Name", artist.getNameEn());
        assertEquals("Machine Description", artist.getDescriptionEn());
    }

    // ---------- Performance ----------

    @Test
    void 공연_수동번역_이후에는_자동번역이_덮어쓰지_않는다() {
        Performance performance = Performance.create(
                LocalDate.now(), LocalTime.NOON, LocalTime.NOON, null, "메인 스테이지");
        performance.applyManualTranslation("Manual Stage");

        performance.applyTranslation("Machine Stage");

        assertEquals("Manual Stage", performance.getStageEn());
        assertTrue(performance.isEnIsManual());
    }

    @Test
    void 공연_수동_플래그가_켜져도_비어있는_스테이지는_자동번역으로_채워진다() {
        Performance performance = Performance.create(
                LocalDate.now(), LocalTime.NOON, LocalTime.NOON, null, "메인 스테이지");
        performance.applyManualTranslation(null);

        performance.applyTranslation("Machine Stage");

        assertEquals("Machine Stage", performance.getStageEn());
        assertTrue(performance.isEnIsManual());
    }
}

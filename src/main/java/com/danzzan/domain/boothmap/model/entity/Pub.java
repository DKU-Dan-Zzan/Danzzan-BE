package com.danzzan.domain.boothmap.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "pub")
public class Pub {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id", nullable = false)
    private College college;

    @Column(name = "department", nullable = false)
    private String department;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "intro")
    private String intro;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "instagram")
    private String instagram;

    @OneToMany(mappedBy = "pub", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<PubImage> images = new ArrayList<>();

    @OneToMany(
            mappedBy = "pub",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PubDisplayDay> displayDays = new ArrayList<>();

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "intro_en")
    private String introEn;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "department_en")
    private String departmentEn;

    @Column(name = "en_is_manual", nullable = false)
    private boolean enIsManual = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public Pub(College college, String department, String name, String intro, String description, String instagram) {
        this.college = college;
        this.department = department;
        this.name = name;
        this.intro = intro;
        this.description = description;
        this.instagram = instagram;
    }

    public void updateAdminInfo(String name, String intro, String description, String instagram) {
        this.name = name;
        this.intro = intro;
        this.description = description;
        this.instagram = instagram;
    }

    /**
     * 요청된 PubOperation 목록과 현재 displayDays를 비교하여 차이만 반영한다.
     * - 기존에 있고 요청에도 있는 row는 그대로 둔다.
     * - 기존에 없는 row만 신규 insert.
     * - 기존에 있지만 요청에 없는 row만 orphanRemoval로 삭제.
     * 같은 (pub_id, pub_operation_id) 조합을 다시 insert하지 않으므로
     * uq_pub_display_day 유니크 충돌이 발생하지 않는다.
     */
    public void replaceDisplayDays(List<PubOperation> pubOperations) {
        Set<Long> requestedOperationIds = pubOperations.stream()
                .map(PubOperation::getId)
                .collect(Collectors.toCollection(HashSet::new));

        // 요청에 없는 기존 row는 제거 (orphanRemoval로 DELETE)
        this.displayDays.removeIf(displayDay ->
                !requestedOperationIds.contains(displayDay.getPubOperation().getId())
        );

        // 이미 보존된 operation id 집합
        Set<Long> retainedOperationIds = this.displayDays.stream()
                .map(displayDay -> displayDay.getPubOperation().getId())
                .collect(Collectors.toCollection(HashSet::new));

        // 신규 row만 추가 (요청에는 있는데 기존에는 없는 것)
        pubOperations.stream()
                .filter(pubOperation -> !retainedOperationIds.contains(pubOperation.getId()))
                .forEach(pubOperation -> this.displayDays.add(new PubDisplayDay(this, pubOperation)));
    }

    /**
     * 기계번역 결과를 반영한다. 보호는 필드 단위다.
     * null 인자는 "번역하지 못했다"는 뜻이므로 기존 값을 지우지 않는다.
     */
    public void applyTranslation(String nameEn, String introEn,
                                 String descriptionEn, String departmentEn) {
        if (nameEn != null && (!this.enIsManual || this.nameEn == null)) {
            this.nameEn = nameEn;
        }
        if (introEn != null && (!this.enIsManual || this.introEn == null)) {
            this.introEn = introEn;
        }
        if (descriptionEn != null && (!this.enIsManual || this.descriptionEn == null)) {
            this.descriptionEn = descriptionEn;
        }
        if (departmentEn != null && (!this.enIsManual || this.departmentEn == null)) {
            this.departmentEn = departmentEn;
        }
    }

    /**
     * 관리자가 직접 입력한 번역을 반영하고 수동 플래그를 켠다.
     */
    public void applyManualTranslation(String nameEn, String introEn,
                                       String descriptionEn, String departmentEn) {
        this.nameEn = nameEn;
        this.introEn = introEn;
        this.descriptionEn = descriptionEn;
        this.departmentEn = departmentEn;
        this.enIsManual = true;
    }
}

package com.danzzan.domain.admin.map;

import com.danzzan.domain.admin.map.dto.request.CreateAdminBoothRequest;
import com.danzzan.domain.admin.map.dto.request.CreateAdminPubRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminBoothRequest;
import com.danzzan.domain.admin.map.dto.request.UpdateAdminPubRequest;
import com.danzzan.domain.admin.map.service.AdminBoothManagementService;
import com.danzzan.domain.boothmap.model.entity.Booth;
import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import com.danzzan.domain.boothmap.model.entity.BoothType;
import com.danzzan.domain.boothmap.model.entity.College;
import com.danzzan.domain.boothmap.model.entity.Pub;
import com.danzzan.domain.boothmap.model.entity.PubOperation;
import com.danzzan.domain.boothmap.repository.BoothOperationRepository;
import com.danzzan.domain.boothmap.repository.BoothRepository;
import com.danzzan.domain.boothmap.repository.CollegeRepository;
import com.danzzan.domain.boothmap.repository.PubImageRepository;
import com.danzzan.domain.boothmap.repository.PubOperationRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.s3.S3Uploader;
import com.danzzan.infra.translation.TranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminBoothManagementService의 주점(Pub) 생성/수정 번역 연결을 검증한다.
 * NoticeServiceTranslationTest와 같은 형태: 생성 시 항상 번역하고,
 * 수정 시에는 한국어가 실제로 바뀐 경우에만 재번역한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminBoothManagementServiceTranslationTest {

    @Mock
    private BoothRepository boothRepository;
    @Mock
    private BoothOperationRepository boothOperationRepository;
    @Mock
    private CollegeRepository collegeRepository;
    @Mock
    private PubRepository pubRepository;
    @Mock
    private PubImageRepository pubImageRepository;
    @Mock
    private PubOperationRepository pubOperationRepository;
    @Mock
    private S3PresignService s3PresignService;
    @Mock
    private S3Uploader s3Uploader;
    @Mock
    private TranslationService translationService;

    @InjectMocks
    private AdminBoothManagementService adminBoothManagementService;

    private College college() {
        College college = BeanUtils.instantiateClass(College.class);
        ReflectionTestUtils.setField(college, "id", 1L);
        ReflectionTestUtils.setField(college, "name", "공과대학");
        return college;
    }

    private CreateAdminPubRequest createRequest() {
        CreateAdminPubRequest request = new CreateAdminPubRequest();
        ReflectionTestUtils.setField(request, "collegeId", 1L);
        ReflectionTestUtils.setField(request, "department", "기계공학과");
        ReflectionTestUtils.setField(request, "name", "기계공학과 주점");
        ReflectionTestUtils.setField(request, "intro", "환영합니다");
        ReflectionTestUtils.setField(request, "description", "즐거운 시간");
        ReflectionTestUtils.setField(request, "displayOperationIds", List.of());
        return request;
    }

    @Test
    void 주점_생성시_영문을_함께_저장한다() {
        when(collegeRepository.findById(1L)).thenReturn(Optional.of(college()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Mechanical Pub", "Welcome", "Good times", "Mechanical Engineering"));
        when(pubRepository.save(any(Pub.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adminBoothManagementService.createPubManagement(createRequest());

        ArgumentCaptor<Pub> captor = ArgumentCaptor.forClass(Pub.class);
        verify(pubRepository).save(captor.capture());

        assertEquals("Mechanical Pub", captor.getValue().getNameEn());
        assertEquals("Welcome", captor.getValue().getIntroEn());
        assertEquals("Good times", captor.getValue().getDescriptionEn());
        assertEquals("Mechanical Engineering", captor.getValue().getDepartmentEn());
    }

    private Pub existingPub() {
        return new Pub(college(), "기계공학과", "기계공학과 주점", "환영합니다", "즐거운 시간", null);
    }

    private UpdateAdminPubRequest updateRequest(String name, String intro, String description) {
        UpdateAdminPubRequest request = new UpdateAdminPubRequest();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "intro", intro);
        ReflectionTestUtils.setField(request, "description", description);
        ReflectionTestUtils.setField(request, "displayOperationIds", List.of());
        return request;
    }

    @Test
    void 주점_한국어가_바뀌면_재번역한다() {
        Pub pub = existingPub();
        when(pubRepository.findById(1L)).thenReturn(Optional.of(pub));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("New Name", "New Intro", "New Description", "Mechanical Engineering"));

        adminBoothManagementService.updatePubManagement(1L, updateRequest("새 이름", "새 소개", "새 설명"));

        verify(translationService).translateAll(any());
        assertEquals("New Name", pub.getNameEn());
        assertEquals("New Intro", pub.getIntroEn());
        assertEquals("New Description", pub.getDescriptionEn());
    }

    @Test
    void 주점_한국어가_바뀌지_않으면_재번역하지_않는다() {
        Pub pub = existingPub();
        when(pubRepository.findById(1L)).thenReturn(Optional.of(pub));

        adminBoothManagementService.updatePubManagement(
                1L, updateRequest("기계공학과 주점", "환영합니다", "즐거운 시간"));

        verify(translationService, never()).translateAll(any());
    }

    @Test
    void 주점_생성시_영문을_모두_직접_입력하면_그대로_저장되고_수동_플래그가_켜진다() {
        when(collegeRepository.findById(1L)).thenReturn(Optional.of(college()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Auto Name", "Auto Intro", "Auto Desc", "Auto Dept"));
        when(pubRepository.save(any(Pub.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateAdminPubRequest request = createRequest();
        ReflectionTestUtils.setField(request, "nameEn", "Manual Name");
        ReflectionTestUtils.setField(request, "introEn", "Manual Intro");
        ReflectionTestUtils.setField(request, "descriptionEn", "Manual Desc");
        ReflectionTestUtils.setField(request, "departmentEn", "Manual Dept");

        adminBoothManagementService.createPubManagement(request);

        ArgumentCaptor<Pub> captor = ArgumentCaptor.forClass(Pub.class);
        verify(pubRepository).save(captor.capture());

        Pub saved = captor.getValue();
        assertEquals("Manual Name", saved.getNameEn());
        assertEquals("Manual Intro", saved.getIntroEn());
        assertEquals("Manual Desc", saved.getDescriptionEn());
        assertEquals("Manual Dept", saved.getDepartmentEn());
        assertEquals(true, saved.isEnIsManual());
    }

    @Test
    void 주점_생성시_일부_영문만_비워두면_비운_필드만_자동번역된다() {
        when(collegeRepository.findById(1L)).thenReturn(Optional.of(college()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Auto Name", "Auto Intro", "Auto Desc", "Auto Dept"));
        when(pubRepository.save(any(Pub.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateAdminPubRequest request = createRequest();
        ReflectionTestUtils.setField(request, "nameEn", "Manual Name");
        // introEn, descriptionEn, departmentEn 비워둠 -> 자동번역 결과가 채워져야 함

        adminBoothManagementService.createPubManagement(request);

        ArgumentCaptor<Pub> captor = ArgumentCaptor.forClass(Pub.class);
        verify(pubRepository).save(captor.capture());

        Pub saved = captor.getValue();
        assertEquals("Manual Name", saved.getNameEn());
        assertEquals("Auto Intro", saved.getIntroEn());
        assertEquals("Auto Desc", saved.getDescriptionEn());
        assertEquals("Auto Dept", saved.getDepartmentEn());
        assertEquals(true, saved.isEnIsManual());
    }

    @Test
    void 주점_수정시_이미_수동인_주점의_한국어를_바꾸고_영문을_비우면_새로_번역된다() {
        Pub pub = existingPub();
        pub.applyManualTranslation("Old Name EN", "Old Intro EN", "Old Desc EN", "Old Dept EN");
        when(pubRepository.findById(1L)).thenReturn(Optional.of(pub));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Fresh Name EN", "Stale Intro Auto", "Stale Desc Auto", "Stale Dept Auto"));

        // name의 한국어만 바꾸고, nameEn은 비워둔다 (나머지 한국어/영문은 그대로)
        adminBoothManagementService.updatePubManagement(
                1L, updateRequest("새 이름", "환영합니다", "즐거운 시간"));

        verify(translationService).translateAll(any());
        assertEquals("Fresh Name EN", pub.getNameEn());
        assertEquals("Old Intro EN", pub.getIntroEn());
        assertEquals("Old Desc EN", pub.getDescriptionEn());
        assertEquals("Old Dept EN", pub.getDepartmentEn());
        assertEquals(true, pub.isEnIsManual());
    }

    @Test
    void 주점_수정시_한국어도_영문도_안바뀌면_재번역_호출이_없고_기존_수동_값이_유지된다() {
        Pub pub = existingPub();
        pub.applyManualTranslation("Old Name EN", "Old Intro EN", "Old Desc EN", "Old Dept EN");
        when(pubRepository.findById(1L)).thenReturn(Optional.of(pub));

        adminBoothManagementService.updatePubManagement(
                1L, updateRequest("기계공학과 주점", "환영합니다", "즐거운 시간"));

        verify(translationService, never()).translateAll(any());
        assertEquals("Old Name EN", pub.getNameEn());
        assertEquals("Old Intro EN", pub.getIntroEn());
        assertEquals("Old Desc EN", pub.getDescriptionEn());
        assertEquals("Old Dept EN", pub.getDepartmentEn());
        assertEquals(true, pub.isEnIsManual());
    }

    private CreateAdminBoothRequest createBoothRequest() {
        CreateAdminBoothRequest request = new CreateAdminBoothRequest();
        ReflectionTestUtils.setField(request, "type", BoothType.FOOD_TRUCK);
        ReflectionTestUtils.setField(request, "name", "떡볶이 부스");
        ReflectionTestUtils.setField(request, "description", "매운맛 주의");
        ReflectionTestUtils.setField(request, "operationStatus", BoothOperationStatus.OPEN);
        ReflectionTestUtils.setField(request, "startTime", LocalTime.of(11, 0));
        ReflectionTestUtils.setField(request, "endTime", LocalTime.of(22, 0));
        ReflectionTestUtils.setField(request, "operationDates", List.of(LocalDate.of(2026, 5, 13)));
        return request;
    }

    private PubOperation supportedOperation() {
        return new PubOperation(LocalDate.of(2026, 5, 13), LocalTime.of(11, 0), LocalTime.of(22, 0));
    }

    @Test
    void 부스_생성시_영문을_함께_저장한다() {
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Tteokbokki Booth", "Spicy warning"));
        when(boothRepository.save(any(Booth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        adminBoothManagementService.createBoothManagement(createBoothRequest());

        ArgumentCaptor<Booth> captor = ArgumentCaptor.forClass(Booth.class);
        verify(boothRepository).save(captor.capture());

        assertEquals("Tteokbokki Booth", captor.getValue().getNameEn());
        assertEquals("Spicy warning", captor.getValue().getDescriptionEn());
    }

    private Booth existingBooth() {
        return new Booth("떡볶이 부스", BoothType.FOOD_TRUCK, "매운맛 주의", null, null, null);
    }

    private UpdateAdminBoothRequest updateBoothRequest(String name, String description) {
        UpdateAdminBoothRequest request = new UpdateAdminBoothRequest();
        ReflectionTestUtils.setField(request, "operationDate", LocalDate.of(2026, 5, 13));
        ReflectionTestUtils.setField(request, "operationStatus", BoothOperationStatus.OPEN);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "description", description);
        ReflectionTestUtils.setField(request, "startTime", LocalTime.of(11, 0));
        ReflectionTestUtils.setField(request, "endTime", LocalTime.of(22, 0));
        ReflectionTestUtils.setField(request, "operationDates", List.of(LocalDate.of(2026, 5, 13)));
        return request;
    }

    @Test
    void 부스_한국어가_바뀌면_재번역한다() {
        Booth booth = existingBooth();
        when(boothRepository.findById(1L)).thenReturn(Optional.of(booth));
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("New Booth Name", "New Description"));

        adminBoothManagementService.updateBoothManagement(1L, updateBoothRequest("새 부스 이름", "새 설명"));

        verify(translationService).translateAll(any());
        assertEquals("New Booth Name", booth.getNameEn());
        assertEquals("New Description", booth.getDescriptionEn());
    }

    @Test
    void 부스_한국어가_바뀌지_않으면_재번역하지_않는다() {
        Booth booth = existingBooth();
        when(boothRepository.findById(1L)).thenReturn(Optional.of(booth));
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));

        adminBoothManagementService.updateBoothManagement(1L, updateBoothRequest("떡볶이 부스", "매운맛 주의"));

        verify(translationService, never()).translateAll(any());
    }

    @Test
    void 부스_생성시_영문을_모두_직접_입력하면_그대로_저장되고_수동_플래그가_켜진다() {
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Auto Booth Name", "Auto Booth Desc"));
        when(boothRepository.save(any(Booth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateAdminBoothRequest request = createBoothRequest();
        ReflectionTestUtils.setField(request, "nameEn", "Manual Booth Name");
        ReflectionTestUtils.setField(request, "descriptionEn", "Manual Booth Desc");

        adminBoothManagementService.createBoothManagement(request);

        ArgumentCaptor<Booth> captor = ArgumentCaptor.forClass(Booth.class);
        verify(boothRepository).save(captor.capture());

        Booth saved = captor.getValue();
        assertEquals("Manual Booth Name", saved.getNameEn());
        assertEquals("Manual Booth Desc", saved.getDescriptionEn());
        assertEquals(true, saved.isEnIsManual());
    }

    @Test
    void 부스_생성시_일부_영문만_비워두면_비운_필드만_자동번역된다() {
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Auto Booth Name", "Auto Booth Desc"));
        when(boothRepository.save(any(Booth.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateAdminBoothRequest request = createBoothRequest();
        ReflectionTestUtils.setField(request, "nameEn", "Manual Booth Name");
        // descriptionEn은 비워둠 -> 자동번역 결과가 채워져야 함

        adminBoothManagementService.createBoothManagement(request);

        ArgumentCaptor<Booth> captor = ArgumentCaptor.forClass(Booth.class);
        verify(boothRepository).save(captor.capture());

        Booth saved = captor.getValue();
        assertEquals("Manual Booth Name", saved.getNameEn());
        assertEquals("Auto Booth Desc", saved.getDescriptionEn());
        assertEquals(true, saved.isEnIsManual());
    }

    @Test
    void 부스_수정시_이미_수동인_부스의_한국어를_바꾸고_영문을_비우면_새로_번역된다() {
        Booth booth = existingBooth();
        booth.applyManualTranslation("Old Booth Name EN", "Old Booth Desc EN");
        when(boothRepository.findById(1L)).thenReturn(Optional.of(booth));
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("Fresh Booth Name EN", "Stale Desc Auto"));

        // name의 한국어만 바꾸고, nameEn은 비워둔다. description(한국어)은 그대로 두고 descriptionEn도 비워둔다.
        adminBoothManagementService.updateBoothManagement(1L, updateBoothRequest("새 부스 이름", "매운맛 주의"));

        verify(translationService).translateAll(any());
        assertEquals("Fresh Booth Name EN", booth.getNameEn());
        assertEquals("Old Booth Desc EN", booth.getDescriptionEn());
        assertEquals(true, booth.isEnIsManual());
    }

    @Test
    void 부스_수정시_한국어도_영문도_안바뀌면_재번역_호출이_없고_기존_수동_값이_유지된다() {
        Booth booth = existingBooth();
        booth.applyManualTranslation("Old Booth Name EN", "Old Booth Desc EN");
        when(boothRepository.findById(1L)).thenReturn(Optional.of(booth));
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));

        adminBoothManagementService.updateBoothManagement(1L, updateBoothRequest("떡볶이 부스", "매운맛 주의"));

        verify(translationService, never()).translateAll(any());
        assertEquals("Old Booth Name EN", booth.getNameEn());
        assertEquals("Old Booth Desc EN", booth.getDescriptionEn());
        assertEquals(true, booth.isEnIsManual());
    }

    /**
     * 재현 시나리오: enIsManual == false인 부스의 한국어 설명을 비우면,
     * 낡은 영문 설명이 영원히 남아 ?lang=en API로 노출되는 버그.
     * decideEnglish(true, null, "Extra spicy", null)은 null(비우라는 뜻)을 반환하지만,
     * 이전 코드는 applyTranslation의 null-가드에 막혀 이 클리어를 반영하지 못했다.
     */
    @Test
    void 부스_자동번역_상태에서_한국어_설명을_비우면_영문_설명도_비워진다() {
        Booth booth = new Booth("떡볶이 부스", BoothType.FOOD_TRUCK, "매운맛 추가", null, null, null);
        booth.applyTranslation("Tteokbokki Booth", "Extra spicy");
        when(boothRepository.findById(1L)).thenReturn(Optional.of(booth));
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));
        // 한국어 설명이 비어 있으므로 TranslationService는 해당 슬롯에 대해 null을 돌려준다(설계된 동작).
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList("Tteokbokki Booth", null));

        adminBoothManagementService.updateBoothManagement(1L, updateBoothRequest("떡볶이 부스", ""));

        assertEquals(null, booth.getDescriptionEn());
        assertEquals("Tteokbokki Booth", booth.getNameEn());
    }

    /**
     * enIsManual이 자동 수정(자동번역 경로)만으로 true로 바뀌지 않는지 확인한다.
     * 기존 테스트들은 모두 true를 기대하는 케이스뿐이라, 플래그를 잘못 켜는 회귀가
     * 눈에 띄지 않고 통과할 수 있었다.
     */
    @Test
    void 부스_자동_수정으로는_수동_플래그가_켜지지_않는다() {
        Booth booth = existingBooth();
        when(boothRepository.findById(1L)).thenReturn(Optional.of(booth));
        when(pubOperationRepository.findAllByOperationDateIn(any()))
                .thenReturn(List.of(supportedOperation()));
        when(translationService.translateAll(any()))
                .thenReturn(List.of("New Booth Name", "New Description"));

        adminBoothManagementService.updateBoothManagement(1L, updateBoothRequest("새 부스 이름", "매운맛 주의"));

        assertEquals(false, booth.isEnIsManual());
    }

    /**
     * Pub 버전의 같은 재현 시나리오: intro 한국어를 비우면 introEn도 비워져야 한다.
     */
    @Test
    void 주점_자동번역_상태에서_한국어_소개를_비우면_영문_소개도_비워진다() {
        Pub pub = new Pub(college(), "기계공학과", "기계공학과 주점", "환영합니다", "즐거운 시간", null);
        pub.applyTranslation("Mechanical Pub", "Welcome", "Good times", "Mechanical Engineering");
        when(pubRepository.findById(1L)).thenReturn(Optional.of(pub));
        // intro 슬롯만 비어 있으므로 그 슬롯만 null로 돌아온다(설계된 동작).
        when(translationService.translateAll(any()))
                .thenReturn(Arrays.asList("Mechanical Pub", null, "Good times", "Mechanical Engineering"));

        adminBoothManagementService.updatePubManagement(
                1L, updateRequest("기계공학과 주점", "", "즐거운 시간"));

        assertEquals(null, pub.getIntroEn());
        assertEquals("Mechanical Pub", pub.getNameEn());
    }
}

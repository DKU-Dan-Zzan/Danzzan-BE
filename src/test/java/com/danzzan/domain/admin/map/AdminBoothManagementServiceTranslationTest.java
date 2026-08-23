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
}

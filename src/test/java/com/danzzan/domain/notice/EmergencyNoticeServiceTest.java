package com.danzzan.domain.notice;

import com.danzzan.domain.notice.dto.request.UpdateEmergencyRequest;
import com.danzzan.domain.notice.dto.response.EmergencyNoticeResponse;
import com.danzzan.domain.notice.entity.EmergencyNotice;
import com.danzzan.domain.notice.repository.EmergencyNoticeRepository;
import com.danzzan.domain.notice.service.EmergencyNoticeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * get() 은 읽기 전용 트랜잭션에서 실행된다. 그 안에서 save() 를 호출하면
 * "Connection is read-only" 로 500 이 나므로, 조회는 절대 쓰지 않아야 한다.
 *
 * 실제로 긴급공지 행이 없는 상태에서 관리자 페이지를 여는 것만으로 500 이 났다.
 */
@ExtendWith(MockitoExtension.class)
class EmergencyNoticeServiceTest {

    @Mock
    private EmergencyNoticeRepository emergencyNoticeRepository;

    @InjectMocks
    private EmergencyNoticeService emergencyNoticeService;

    @Test
    void 긴급공지가_없을_때_조회는_저장하지_않는다() {
        when(emergencyNoticeRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        emergencyNoticeService.get();

        verify(emergencyNoticeRepository, never()).save(any());
    }

    @Test
    void 긴급공지가_없을_때_조회는_비활성_기본값을_반환한다() {
        when(emergencyNoticeRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        EmergencyNoticeResponse response = emergencyNoticeService.get();

        assertNull(response.getId());
        assertEquals("", response.getMessage());
        assertFalse(response.getIsActive());
    }

    @Test
    void 긴급공지가_있으면_그대로_반환한다() {
        EmergencyNotice existing = new EmergencyNotice();
        existing.setMessage("메인 스테이지 앞이 혼잡합니다");
        existing.setIsActive(true);
        when(emergencyNoticeRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(existing));

        EmergencyNoticeResponse response = emergencyNoticeService.get();

        assertEquals("메인 스테이지 앞이 혼잡합니다", response.getMessage());
        verify(emergencyNoticeRepository, never()).save(any());
    }

    @Test
    void 행이_없는_상태에서_수정하면_새로_만들어_저장한다() {
        when(emergencyNoticeRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(emergencyNoticeRepository.save(any(EmergencyNotice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateEmergencyRequest request = new UpdateEmergencyRequest();
        request.setMessage("우천으로 야외 부스가 중단됩니다");
        request.setIsActive(true);

        EmergencyNoticeResponse response = emergencyNoticeService.update(request);

        assertEquals("우천으로 야외 부스가 중단됩니다", response.getMessage());
        verify(emergencyNoticeRepository).save(any(EmergencyNotice.class));
    }
}

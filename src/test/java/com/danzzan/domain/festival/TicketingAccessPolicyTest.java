package com.danzzan.domain.festival;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.service.TicketingAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 티켓팅을 열지 말지는 관리자 축제 설정이 정한다.
 * app.ticketing.api-enabled 는 그 위에 있는 비상 차단 스위치다.
 */
@ExtendWith(MockitoExtension.class)
class TicketingAccessPolicyTest {

    @Mock
    private FestivalSettingRepository festivalSettingRepository;

    private TicketingAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new TicketingAccessPolicy(festivalSettingRepository);
        ReflectionTestUtils.setField(policy, "apiEnabledSwitch", true);
    }

    @Test
    void 설정에서_켜면_티켓팅이_열린다() {
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID))
                .thenReturn(Optional.of(setting(true)));

        assertTrue(policy.isTicketingEnabled());
    }

    @Test
    void 설정에서_끄면_티켓팅이_닫힌다() {
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID))
                .thenReturn(Optional.of(setting(false)));

        assertFalse(policy.isTicketingEnabled());
    }

    @Test
    void 설정을_저장한_적이_없으면_닫힌다() {
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID))
                .thenReturn(Optional.empty());

        assertFalse(policy.isTicketingEnabled());
    }

    @Test
    void 비상_차단_스위치가_꺼져_있으면_설정과_무관하게_닫힌다() {
        ReflectionTestUtils.setField(policy, "apiEnabledSwitch", false);

        assertFalse(policy.isTicketingEnabled());
        // 볼 것도 없이 막으므로 DB 를 읽지 않는다.
        verify(festivalSettingRepository, never()).findById(FestivalSetting.SINGLETON_ID);
    }

    @Test
    void 티켓팅은_순간_트래픽이_몰리므로_매_요청마다_DB를_읽지_않는다() {
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID))
                .thenReturn(Optional.of(setting(true)));

        policy.isTicketingEnabled();
        policy.isTicketingEnabled();
        policy.isTicketingEnabled();

        verify(festivalSettingRepository, times(1)).findById(FestivalSetting.SINGLETON_ID);
    }

    @Test
    void 설정을_저장하면_캐시를_비워_바로_반영한다() {
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID))
                .thenReturn(Optional.of(setting(false)), Optional.of(setting(true)));

        assertFalse(policy.isTicketingEnabled());
        policy.invalidate();

        assertTrue(policy.isTicketingEnabled());
    }

    private FestivalSetting setting(boolean ticketingEnabled) {
        return FestivalSetting.create(
                "단국대학교",
                "2027 DANFESTA",
                LocalDate.of(2027, 5, 14),
                LocalDate.of(2027, 5, 16),
                ticketingEnabled
        );
    }
}

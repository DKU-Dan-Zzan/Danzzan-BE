package com.danzzan.domain.festival;

import com.danzzan.domain.festival.dto.request.TicketingRoundRequest;
import com.danzzan.domain.festival.dto.request.UpdateFestivalSettingRequest;
import com.danzzan.domain.festival.dto.response.FestivalSettingResponse;
import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.entity.FestivalTicketingRound;
import com.danzzan.domain.festival.exception.InvalidFestivalSettingException;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.repository.FestivalTicketingRoundRepository;
import com.danzzan.domain.festival.service.FestivalSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 축제 설정은 프론트의 날짜 탭이 그대로 따라오는 값이라, 운영 기간 밖의 공연 날짜가
 * 저장되면 티켓은 있는데 그 날짜 탭이 없는 상태가 된다. 그래서 저장 시 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FestivalSettingServiceTest {

    @Mock
    private FestivalSettingRepository festivalSettingRepository;

    @Mock
    private FestivalTicketingRoundRepository festivalTicketingRoundRepository;

    @InjectMocks
    private FestivalSettingService festivalSettingService;

    @Test
    void 저장된_설정이_없으면_빈_설정을_내려준다() {
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        FestivalSettingResponse response = festivalSettingService.getSettings();

        assertEquals("", response.festivalName());
        assertTrue(response.operationDates().isEmpty());
        assertFalse(response.ticketingEnabled());
        // 조회는 읽기 전용 트랜잭션이라 저장을 시도하면 안 된다.
        verify(festivalSettingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 운영_날짜를_하루씩_펼쳐서_내려준다() {
        FestivalSetting setting = FestivalSetting.create(
                "단국대학교", "2026 DANFESTA",
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11), false
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.of(setting));
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());

        FestivalSettingResponse response = festivalSettingService.getSettings();

        assertEquals(
                List.of(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)),
                response.operationDates()
        );
    }

    @Test
    void 종료일이_시작일보다_빠르면_저장하지_않는다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 9), false, List.of()
        );

        assertThrows(InvalidFestivalSettingException.class, () -> festivalSettingService.updateSettings(request));
        verify(festivalSettingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 운영_기간_밖의_공연_날짜는_거절한다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true,
                List.of(round(LocalDateTime.of(2026, 9, 1, 18, 0), 1000, LocalDate.of(2026, 9, 20)))
        );

        assertThrows(InvalidFestivalSettingException.class, () -> festivalSettingService.updateSettings(request));
        verify(festivalSettingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 티켓팅이_꺼져_있으면_회차를_저장하지_않는다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), false,
                List.of(round(LocalDateTime.of(2026, 9, 1, 18, 0), 1000, LocalDate.of(2026, 9, 9)))
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        FestivalSettingResponse response = festivalSettingService.updateSettings(request);

        assertTrue(response.ticketingRounds().isEmpty());
        verify(festivalTicketingRoundRepository).saveAll(List.of());
    }

    @Test
    void 보낸_회차가_저장된_회차를_대신한다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true,
                List.of(
                        round(LocalDateTime.of(2026, 9, 1, 18, 0), 1000, LocalDate.of(2026, 9, 9)),
                        round(LocalDateTime.of(2026, 9, 2, 18, 0), 500, LocalDate.of(2026, 9, 10))
                )
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        FestivalSettingResponse response = festivalSettingService.updateSettings(request);

        verify(festivalTicketingRoundRepository).deleteAllInBatch();
        verify(festivalTicketingRoundRepository).saveAll(anyList());
        assertEquals(2, response.ticketingRounds().size());
        assertEquals(1000, response.ticketingRounds().get(0).capacity());
    }

    @Test
    void 회차는_보낸_순서를_유지한다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true,
                List.of(
                        round(LocalDateTime.of(2026, 9, 2, 18, 0), 500, LocalDate.of(2026, 9, 10)),
                        round(LocalDateTime.of(2026, 9, 1, 18, 0), 1000, LocalDate.of(2026, 9, 9))
                )
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());

        festivalSettingService.updateSettings(request);

        org.mockito.ArgumentCaptor<List<FestivalTicketingRound>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(festivalTicketingRoundRepository).saveAll(captor.capture());
        List<FestivalTicketingRound> saved = captor.getValue();
        assertEquals(0, saved.get(0).getDisplayOrder());
        assertEquals(500, saved.get(0).getCapacity());
        assertEquals(1, saved.get(1).getDisplayOrder());
    }

    private UpdateFestivalSettingRequest request(
            LocalDate startDate,
            LocalDate endDate,
            boolean ticketingEnabled,
            List<TicketingRoundRequest> rounds
    ) {
        UpdateFestivalSettingRequest request = new UpdateFestivalSettingRequest();
        request.setSchoolName("단국대학교");
        request.setFestivalName("2026 DANFESTA");
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setTicketingEnabled(ticketingEnabled);
        request.setTicketingRounds(rounds);
        return request;
    }

    private TicketingRoundRequest round(LocalDateTime ticketingAt, int capacity, LocalDate performanceDate) {
        TicketingRoundRequest round = new TicketingRoundRequest();
        round.setTicketingAt(ticketingAt);
        round.setCapacity(capacity);
        round.setPerformanceDate(performanceDate);
        return round;
    }
}

package com.danzzan.domain.festival;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.festival.dto.request.TicketingRoundRequest;
import com.danzzan.domain.festival.dto.request.UpdateFestivalSettingRequest;
import com.danzzan.domain.festival.dto.response.FestivalSettingResponse;
import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.entity.FestivalTicketingRound;
import com.danzzan.domain.festival.exception.InvalidFestivalSettingException;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.repository.FestivalTicketingRoundRepository;
import com.danzzan.domain.festival.service.FestivalSettingService;
import com.danzzan.domain.festival.service.TicketingAccessPolicy;
import com.danzzan.domain.ticket.repository.TicketQueueEntryRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.ticket.service.TicketInitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Mock
    private FestivalEventRepository festivalEventRepository;

    @Mock
    private UserTicketRepository userTicketRepository;

    @Mock
    private TicketQueueEntryRepository ticketQueueEntryRepository;

    @Mock
    private TicketInitService ticketInitService;

    @Mock
    private TicketingAccessPolicy ticketingAccessPolicy;

    @InjectMocks
    private FestivalSettingService festivalSettingService;

    @org.junit.jupiter.api.BeforeEach
    void lockReturnsTheCurrentEvent() {
        org.mockito.Mockito.lenient().when(festivalEventRepository.findByIdForUpdate(any()))
                .thenAnswer(invocation -> festivalEventRepository.findById(invocation.getArgument(0)));
    }

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
    void 운영_정보_저장은_본문의_티켓팅_필드를_무시하고_기존_회차와_스위치를_보존한다() {
        FestivalSetting setting = FestivalSetting.create("단국대학교", "기존 축제",
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true);
        FestivalTicketingRound savedRound = round(1L, LocalDateTime.of(2026, 9, 1, 18, 0), 100,
                LocalDate.of(2026, 9, 9), 0, 10L);
        UpdateFestivalSettingRequest request = request(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), false, List.of());
        when(festivalSettingRepository.findByIdForUpdate(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.of(setting));
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(savedRound));

        FestivalSettingResponse response = festivalSettingService.updateMetadata(request);

        assertTrue(response.ticketingEnabled());
        assertEquals(1, response.ticketingRounds().size());
        assertEquals("2026 DANFESTA", setting.getFestivalName());
        verify(festivalSettingRepository).save(setting);
        verify(festivalEventRepository, never()).save(any());
        verify(festivalEventRepository, never()).delete(any());
        verifyNoInteractions(ticketQueueEntryRepository, ticketInitService, ticketingAccessPolicy);
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
    void 티켓팅을_꺼도_저장된_회차를_지우지_않는다() {
        // 끄는 것은 "회차를 지운다"가 아니라 "지금은 열지 않는다"이다.
        // 지우면 이미 티켓을 받은 사람의 근거가 사라진다.
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), false, List.of()
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        FestivalTicketingRound saved = round(1L, LocalDateTime.of(2026, 9, 1, 18, 0), 1000,
                LocalDate.of(2026, 9, 9), 0, 10L);
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(saved));
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event(10L, TicketingStatus.READY)));
        when(userTicketRepository.countByEventId(10L)).thenReturn(0L);

        FestivalSettingResponse response = festivalSettingService.updateSettings(request);

        assertEquals(1, response.ticketingRounds().size());
        verify(festivalTicketingRoundRepository, never()).delete(any());
    }

    @Test
    void 새_회차를_저장하면_티켓팅_이벤트도_함께_만든다() {
        // 대기열·티켓 발급은 festival_events 를 본다. 이벤트를 만들지 않으면
        // 설정에 적기만 하고 실제로는 아무 일도 일어나지 않는다.
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true,
                List.of(round(LocalDateTime.of(2026, 9, 1, 18, 0), 1000, LocalDate.of(2026, 9, 9)))
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());
        when(festivalEventRepository.save(any(FestivalEvent.class))).thenAnswer(call -> call.getArgument(0));
        when(festivalTicketingRoundRepository.save(any(FestivalTicketingRound.class)))
                .thenAnswer(call -> call.getArgument(0));

        festivalSettingService.updateSettings(request);

        ArgumentCaptor<FestivalEvent> captor = ArgumentCaptor.forClass(FestivalEvent.class);
        verify(festivalEventRepository).save(captor.capture());
        FestivalEvent created = captor.getValue();
        assertEquals(LocalDateTime.of(2026, 9, 1, 18, 0), created.getTicketingStartTime());
        assertEquals(LocalDate.of(2026, 9, 9), created.getEventDate());
        assertEquals(1000, created.getTotalCapacity());
        // 오픈 시각이 되면 기존 스케줄러가 READY 인 이벤트를 자동으로 연다.
        assertEquals(TicketingStatus.READY, created.getTicketingStatus());
    }

    @Test
    void 티켓이_나간_회차는_확인_없이는_지울_수_없다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true, List.of()
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        FestivalTicketingRound issued = round(1L, LocalDateTime.of(2026, 9, 1, 18, 0), 1000,
                LocalDate.of(2026, 9, 9), 0, 10L);
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(issued));
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event(10L, TicketingStatus.READY)));
        when(userTicketRepository.countByEventId(10L)).thenReturn(3L);

        assertThrows(InvalidFestivalSettingException.class, () -> festivalSettingService.updateSettings(request));
        verify(festivalTicketingRoundRepository, never()).delete(any());
        verify(userTicketRepository, never()).deleteAllByEventId(10L);
    }

    @Test
    void 확인한_회차는_티켓과_대기열까지_지운다() {
        // 관리자가 "발급된 티켓도 함께 취소" 를 확인한 경우다.
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true, List.of()
        );
        request.setConfirmedTicketCancelRoundIds(List.of(1L));
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        FestivalTicketingRound issued = round(1L, LocalDateTime.of(2026, 9, 1, 18, 0), 1000,
                LocalDate.of(2026, 9, 9), 0, 10L);
        FestivalEvent event = event(10L, TicketingStatus.OPEN);
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(issued));
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userTicketRepository.countByEventId(10L)).thenReturn(3L);

        festivalSettingService.updateSettings(request);

        verify(ticketQueueEntryRepository).deleteAllByEventId(10L);
        verify(userTicketRepository).deleteAllByEventId(10L);
        verify(festivalEventRepository).delete(event);
        verify(festivalTicketingRoundRepository).delete(issued);
        // 키를 남기면 같은 id 의 다음 이벤트가 예전 재고를 물려받는다.
        verify(ticketInitService).purgeEvent("10");
    }

    @Test
    void 티켓이_없으면_오픈된_회차도_확인_없이_지운다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true, List.of()
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        FestivalTicketingRound open = round(1L, LocalDateTime.of(2026, 9, 1, 18, 0), 1000,
                LocalDate.of(2026, 9, 9), 0, 10L);
        FestivalEvent event = event(10L, TicketingStatus.OPEN);
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(open));
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userTicketRepository.countByEventId(10L)).thenReturn(0L);

        festivalSettingService.updateSettings(request);

        verify(festivalTicketingRoundRepository).delete(open);
        verify(festivalEventRepository).delete(event);
    }

    @Test
    void 이미_오픈한_회차는_고칠_수_없다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true,
                List.of(round(1L, LocalDateTime.of(2026, 9, 2, 18, 0), 2000, LocalDate.of(2026, 9, 10)))
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        FestivalTicketingRound open = round(1L, LocalDateTime.of(2026, 9, 1, 18, 0), 1000,
                LocalDate.of(2026, 9, 9), 0, 10L);
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(open));
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event(10L, TicketingStatus.OPEN)));

        assertThrows(InvalidFestivalSettingException.class, () -> festivalSettingService.updateSettings(request));
    }

    @Test
    void 화면에서_뺀_회차는_이벤트와_함께_지운다() {
        UpdateFestivalSettingRequest request = request(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), true, List.of()
        );
        when(festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)).thenReturn(Optional.empty());
        FestivalTicketingRound removed = round(1L, LocalDateTime.of(2026, 9, 1, 18, 0), 1000,
                LocalDate.of(2026, 9, 9), 0, 10L);
        FestivalEvent event = event(10L, TicketingStatus.READY);
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(removed));
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(userTicketRepository.countByEventId(10L)).thenReturn(0L);

        festivalSettingService.updateSettings(request);

        verify(festivalEventRepository).delete(event);
        verify(festivalTicketingRoundRepository).delete(removed);
    }

    @Test
    void unchangedOpenRoundSurvivesSettingsSave() {
        var request = request(LocalDate.of(2026,9,9), LocalDate.of(2026,9,10), true,
                List.of(round(1L, LocalDateTime.of(2026,9,1,18,0), 1000, LocalDate.of(2026,9,9))));
        var saved = round(1L, LocalDateTime.of(2026,9,1,18,0),1000,LocalDate.of(2026,9,9),0,10L);
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(saved));
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event(10L, TicketingStatus.OPEN)));
        festivalSettingService.updateSettings(request);
        verify(festivalEventRepository, never()).save(any());
    }

    @Test
    void unknownRoundIdCannotCreateAnotherEvent() {
        var request = request(LocalDate.of(2026,9,9), LocalDate.of(2026,9,10), true,
                List.of(round(999L, LocalDateTime.of(2026,9,1,18,0),1000,LocalDate.of(2026,9,9))));
        assertThrows(InvalidFestivalSettingException.class, () -> festivalSettingService.updateSettings(request));
        verify(festivalEventRepository, never()).save(any());
    }

    @Test
    void backgroundOmissionPreservesAndExplicitNullClearsWithoutDeletingRounds() throws Exception {
        var setting = FestivalSetting.create("학교", "축제", LocalDate.of(2027,5,1), LocalDate.of(2027,5,2), false);
        setting.updateTicketingBackgroundImageUrl("https://example.com/old.png");
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = mapper.readValue("{\"ticketingEnabled\":false,\"ticketingRounds\":[]}", com.danzzan.domain.festival.dto.request.UpdateFestivalTicketingSettingsRequest.class);
        assertEquals("https://example.com/old.png", festivalSettingService.updateTicketingSettings(request).ticketingBackgroundImageUrl());
        request = mapper.readValue("{\"ticketingEnabled\":false,\"ticketingRounds\":[],\"ticketingBackgroundImageUrl\":null}", com.danzzan.domain.festival.dto.request.UpdateFestivalTicketingSettingsRequest.class);
        org.junit.jupiter.api.Assertions.assertNull(festivalSettingService.updateTicketingSettings(request).ticketingBackgroundImageUrl());
        verifyNoInteractions(ticketQueueEntryRepository, userTicketRepository, festivalEventRepository);
        verify(festivalTicketingRoundRepository, never()).delete(any());
    }

    @Test
    void titlesUsePerformanceDayNotRoundOrder() {
        var request = request(LocalDate.of(2027,5,1), LocalDate.of(2027,5,3), true,
                List.of(round(LocalDateTime.of(2027,4,28,18,0), 100, LocalDate.of(2027,5,2)),
                        round(LocalDateTime.of(2027,4,29,19,0), 200, LocalDate.of(2027,5,3))));
        when(festivalEventRepository.save(any(FestivalEvent.class))).thenAnswer(call -> call.getArgument(0));
        when(festivalTicketingRoundRepository.save(any(FestivalTicketingRound.class))).thenAnswer(call -> call.getArgument(0));
        festivalSettingService.updateSettings(request);
        var events = ArgumentCaptor.forClass(FestivalEvent.class);
        verify(festivalEventRepository, org.mockito.Mockito.times(2)).save(events.capture());
        assertEquals(List.of("2026 DANFESTA DAY 2", "2026 DANFESTA DAY 3"),
                events.getAllValues().stream().map(FestivalEvent::getTitle).toList());
    }

    @Test
    void ticketCardBackgroundIsIndependentAndOmissionPreservesIt() throws Exception {
        var setting = FestivalSetting.create("학교", "축제", LocalDate.of(2027,5,1), LocalDate.of(2027,5,3), true);
        setting.updateTicketingBackgroundImageUrl("https://example.com/off.png");
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var type = com.danzzan.domain.festival.dto.request.UpdateFestivalTicketingSettingsRequest.class;
        var request = mapper.readValue("{\"ticketingEnabled\":true,\"ticketingRounds\":[],\"ticketCardBackgroundImageUrl\":\"https://example.com/on.png\"}", type);
        var response = mapper.valueToTree(festivalSettingService.updateTicketingSettings(request));
        assertEquals("https://example.com/on.png", response.path("ticketCardBackgroundImageUrl").asText());
        assertEquals("https://example.com/off.png", response.path("ticketingBackgroundImageUrl").asText());
        response = mapper.valueToTree(festivalSettingService.updateTicketingSettings(mapper.readValue("{\"ticketingEnabled\":false,\"ticketingRounds\":[]}", type)));
        assertEquals("https://example.com/on.png", response.path("ticketCardBackgroundImageUrl").asText());
        response = mapper.valueToTree(festivalSettingService.updateTicketingSettings(mapper.readValue("{\"ticketingEnabled\":true,\"ticketingRounds\":[],\"ticketCardBackgroundImageUrl\":null}", type)));
        assertTrue(response.path("ticketCardBackgroundImageUrl").isNull());
        assertEquals("https://example.com/off.png", response.path("ticketingBackgroundImageUrl").asText());
    }

    @Test
    void metadataRenameUpdatesAnOpenTicketTitleWithoutChangingStockOrTime() {
        var setting = FestivalSetting.create("학교", "이전 축제", LocalDate.of(2026,9,8), LocalDate.of(2026,9,10), true);
        var existing = round(1L, LocalDateTime.of(2026,9,1,18,0),1000,LocalDate.of(2026,9,9),0,10L);
        var event = event(10L, TicketingStatus.OPEN);
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(existing));
        when(festivalEventRepository.findById(10L)).thenReturn(Optional.of(event));
        var request = request(LocalDate.of(2026,9,8), LocalDate.of(2026,9,10), true, List.of());
        request.setFestivalName("새 축제");
        festivalSettingService.updateMetadata(request);
        assertEquals("새 축제 DAY 2", event.getTitle());
        assertEquals(TicketingStatus.OPEN, event.getTicketingStatus());
        assertEquals(1000, event.getTotalCapacity());
        assertEquals(LocalDateTime.of(2026,9,1,18,0), event.getTicketingStartTime());
        verifyNoInteractions(ticketInitService);
    }

    @Test
    void metadataCannotExcludeAnExistingPerformanceDate() {
        var setting = FestivalSetting.create("학교", "축제", LocalDate.of(2026,9,8), LocalDate.of(2026,9,10), true);
        when(festivalSettingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(setting));
        when(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                round(1L, LocalDateTime.of(2026,9,1,18,0),1000,LocalDate.of(2026,9,9),0,10L)));
        assertThrows(InvalidFestivalSettingException.class, () -> festivalSettingService.updateMetadata(
                request(LocalDate.of(2026,10,1), LocalDate.of(2026,10,3),true,List.of())));
        assertEquals(LocalDate.of(2026,9,8), setting.getStartDate());
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

    private TicketingRoundRequest round(Long id, LocalDateTime ticketingAt, int capacity, LocalDate performanceDate) {
        TicketingRoundRequest round = round(ticketingAt, capacity, performanceDate);
        round.setId(id);
        return round;
    }

    private FestivalTicketingRound round(
            Long id, LocalDateTime ticketingAt, int capacity, LocalDate performanceDate, int order, Long eventId
    ) {
        FestivalTicketingRound round = FestivalTicketingRound.create(ticketingAt, capacity, performanceDate, order);
        ReflectionTestUtils.setField(round, "id", id);
        round.linkEvent(eventId);
        return round;
    }

    private FestivalEvent event(Long id, TicketingStatus status) {
        FestivalEvent event = FestivalEvent.builder()
                .title("2026 DANFESTA 1회차")
                .eventDate(LocalDate.of(2026, 9, 9))
                .ticketingStartTime(LocalDateTime.of(2026, 9, 1, 18, 0))
                .ticketingStatus(status)
                .totalCapacity(1000)
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}

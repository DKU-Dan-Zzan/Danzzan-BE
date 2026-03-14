package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.model.entity.QueueEntryStatus;
import com.danzzan.domain.ticket.model.entity.TicketQueueEntry;
import com.danzzan.domain.ticket.repository.TicketQueueEntryRepository;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TicketQueueEntrySyncIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueStateService queueStateService;

    @Autowired
    private TicketQueueEntrySyncService ticketQueueEntrySyncService;

    @Autowired
    private TicketInitService ticketInitService;

    @Autowired
    private TicketQueueEntryRepository ticketQueueEntryRepository;

    @Autowired
    private FestivalEventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private final List<Long> eventIdsToClean = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ticketQueueEntryRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
        eventIdsToClean.clear();
    }

    @AfterEach
    void tearDown() {
        for (Long eventId : eventIdsToClean) {
            ticketInitService.initStock(String.valueOf(eventId), 0L);
        }
        ticketQueueEntryRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void enterQueue_성공하면_ticketQueueEntries에_WAITING이_저장된다() {
        User user = saveUser("32100001");
        FestivalEvent event = saveOpenEvent("대기열 테스트 1");
        prepareEvent(event, 10L);

        long seq = queueService.enterQueue(event.getId().toString(), user.getId().toString());

        TicketQueueEntry entry = findEntry(event, user);
        assertThat(seq).isPositive();
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.WAITING);
        assertThat(entry.getSeq()).isEqualTo(seq);
        assertThat(entry.getEnteredAt()).isNotNull();
        assertThat(entry.getReadyUntil()).isNull();
        assertThat(entry.getLeaseUntil()).isNull();
    }

    @Test
    void admit후_activate까지_성공하면_ticketQueueEntries가_ACTIVE로_동기화된다() {
        User user = saveUser("32100002");
        FestivalEvent event = saveOpenEvent("대기열 테스트 2");
        prepareEvent(event, 10L);

        queueService.enterQueue(event.getId().toString(), user.getId().toString());
        long readyUntilMs = System.currentTimeMillis() + 60_000L;

        boolean admitted = queueStateService.admitNextWaitingUser(
                event.getId().toString(),
                readyUntilMs,
                100
        );
        long activated = queueStateService.activateIfReady(event.getId().toString(), user.getId().toString());

        TicketQueueEntry entry = findEntry(event, user);
        assertThat(admitted).isTrue();
        assertThat(activated).isPositive();
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.ACTIVE);
        assertThat(entry.getReadyUntil()).isNotNull();
        assertThat(entry.getLeaseUntil()).isNotNull();
    }

    @Test
    void 후속_READY동기화시_row가_없으면_Redis스냅샷으로_재생성한다() {
        User user = saveUser("32100003");
        FestivalEvent event = saveOpenEvent("대기열 테스트 3");
        prepareEvent(event, 10L);

        long seq = queueService.enterQueue(event.getId().toString(), user.getId().toString());
        ticketQueueEntryRepository.deleteAll();
        assertThat(ticketQueueEntryRepository.findByEventIdAndUserId(event.getId(), user.getId())).isEmpty();

        long readyUntilMs = System.currentTimeMillis() + 60_000L;
        boolean admitted = queueStateService.admitNextWaitingUser(event.getId().toString(), readyUntilMs, 100);

        assertThat(admitted).isTrue();
        TicketQueueEntry entry = findEntry(event, user);
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.READY);
        assertThat(entry.getSeq()).isEqualTo(seq);
        assertThat(entry.getEnteredAt()).isNotNull();
        assertThat(entry.getReadyUntil()).isNotNull();
    }

    @Test
    void 후속_FAILED동기화시_row가_없으면_Redis스냅샷으로_재생성한뒤_FAILED로_마감한다() {
        User user = saveUser("32100004");
        FestivalEvent event = saveOpenEvent("대기열 테스트 4");
        prepareEvent(event, 10L);

        queueService.enterQueue(event.getId().toString(), user.getId().toString());
        boolean admitted = queueStateService.admitNextWaitingUser(
                event.getId().toString(),
                System.currentTimeMillis() + 60_000L,
                100
        );
        assertThat(admitted).isTrue();
        long activated = queueStateService.activateIfReady(event.getId().toString(), user.getId().toString());
        assertThat(activated).isPositive();

        ticketQueueEntryRepository.deleteAll();
        assertThat(ticketQueueEntryRepository.findByEventIdAndUserId(event.getId(), user.getId())).isEmpty();

        ticketQueueEntrySyncService.markFailed(event.getId().toString(), user.getId().toString());

        TicketQueueEntry entry = findEntry(event, user);
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.FAILED);
        assertThat(entry.getEnteredAt()).isNotNull();
        assertThat(entry.getReadyUntil()).isNull();
        assertThat(entry.getLeaseUntil()).isNull();
    }

    private void prepareEvent(FestivalEvent event, long stock) {
        eventIdsToClean.add(event.getId());
        ticketInitService.initStock(String.valueOf(event.getId()), stock);
    }

    private TicketQueueEntry findEntry(FestivalEvent event, User user) {
        return ticketQueueEntryRepository.findByEventIdAndUserId(event.getId(), user.getId())
                .orElseThrow();
    }

    private FestivalEvent saveOpenEvent(String title) {
        return eventRepository.save(FestivalEvent.builder()
                .title(title)
                .eventDate(LocalDate.now().plusDays(7))
                .ticketingStartTime(LocalDateTime.now().minusMinutes(10))
                .ticketingStatus(TicketingStatus.OPEN)
                .totalCapacity(100)
                .build());
    }

    private User saveUser(String studentId) {
        return userRepository.save(User.builder()
                .studentId(studentId)
                .password("encoded-password")
                .name("큐테스터")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build());
    }
}

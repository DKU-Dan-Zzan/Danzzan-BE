package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.controller.TicketController;
import com.danzzan.domain.ticket.model.entity.QueueEntryStatus;
import com.danzzan.domain.ticket.model.entity.TicketQueueEntry;
import com.danzzan.domain.ticket.repository.TicketQueueEntryRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TicketQueueConcurrencyIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueStateService queueStateService;

    @Autowired
    private TicketController ticketController;

    @Autowired
    private TicketInitService ticketInitService;

    @Autowired
    private TicketQueueEntryRepository ticketQueueEntryRepository;

    @Autowired
    private UserTicketRepository userTicketRepository;

    @Autowired
    private FestivalEventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    private final List<Long> eventIdsToClean = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userTicketRepository.deleteAll();
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
        userTicketRepository.deleteAll();
        ticketQueueEntryRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 동시_queueEnter는_seq를_유일하게_발급한다() throws Exception {
        FestivalEvent event = saveOpenEvent("동시 진입 테스트");
        prepareEvent(event, 20L);
        List<User> users = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> saveUser("3220000" + i))
                .toList();

        Set<Long> sequences = runConcurrently(users.size(), index ->
                queueService.enterQueue(event.getId().toString(), users.get(index).getId().toString())
        ).stream().map(result -> (Long) result).collect(Collectors.toSet());

        assertThat(sequences).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(ticketQueueEntryRepository.findAll()).hasSize(10);
        assertThat(ticketQueueEntryRepository.findAll().stream().map(TicketQueueEntry::getStatus).collect(Collectors.toSet()))
                .containsExactly(QueueEntryStatus.WAITING);
    }

    @Test
    void 같은_사용자_동시_queueEnter는_한번만_성공한다() throws Exception {
        FestivalEvent event = saveOpenEvent("중복 진입 테스트");
        prepareEvent(event, 20L);
        User user = saveUser("32200020");

        List<Object> results = runConcurrently(10, ignored ->
                queueService.enterQueue(event.getId().toString(), user.getId().toString())
        );

        long successCount = results.stream().map(Long.class::cast).filter(value -> value > 0).count();
        long duplicateCount = results.stream().map(Long.class::cast).filter(value -> value == 0L).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(duplicateCount).isEqualTo(9);
        assertThat(ticketQueueEntryRepository.findAll()).hasSize(1);
    }

    @Test
    void 같은_READY사용자_동시_activate는_하나의_ACTIVE만_유지한다() throws Exception {
        FestivalEvent event = saveOpenEvent("동시 activate 테스트");
        prepareEvent(event, 20L);
        User user = saveUser("32200030");
        queueService.enterQueue(event.getId().toString(), user.getId().toString());
        boolean admitted = queueStateService.admitNextWaitingUser(
                event.getId().toString(),
                System.currentTimeMillis() + 60_000L,
                100
        );
        assertThat(admitted).isTrue();

        List<Object> results = runConcurrently(10, ignored ->
                queueStateService.activateIfReady(event.getId().toString(), user.getId().toString())
        );

        assertThat(results.stream().map(Long.class::cast).allMatch(value -> value > 0L)).isTrue();
        assertThat(queueStateService.activeCount(event.getId().toString())).isEqualTo(1);
        assertThat(findEntry(event, user).getStatus()).isEqualTo(QueueEntryStatus.ACTIVE);
    }

    @Test
    void 같은_ACTIVE사용자_동시_reserve는_티켓한장만_발급되고_projection은_DONE으로_유지된다() throws Exception {
        FestivalEvent event = saveOpenEvent("동시 reserve 테스트");
        prepareEvent(event, 10L);
        User user = saveUser("32200040");
        activateUser(event, user);

        List<Object> results = runConcurrently(10, ignored -> {
            try {
                ticketController.reserveTicket(event.getId(), new TestingAuthenticationToken(user.getId(), null));
                return "SUCCESS";
            } catch (Exception e) {
                return e.getClass().getSimpleName();
            }
        });

        long successCount = results.stream().filter("SUCCESS"::equals).count();
        TicketQueueEntry entry = findEntry(event, user);

        assertThat(successCount).isEqualTo(1);
        assertThat(userTicketRepository.count()).isEqualTo(1);
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.DONE);
        assertThat(queueStateService.activeCount(event.getId().toString())).isZero();
    }

    private List<Object> runConcurrently(int threadCount, ConcurrentAction action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ConcurrentLinkedQueue<Object> results = new ConcurrentLinkedQueue<>();

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await(5, TimeUnit.SECONDS);
                    results.add(action.run(index));
                    return null;
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            return new ArrayList<>(results);
        } finally {
            executor.shutdownNow();
        }
    }

    private void activateUser(FestivalEvent event, User user) {
        String eventId = event.getId().toString();
        String userId = user.getId().toString();
        queueService.enterQueue(eventId, userId);
        boolean admitted = queueStateService.admitNextWaitingUser(eventId, System.currentTimeMillis() + 60_000L, 100);
        assertThat(admitted).isTrue();
        assertThat(queueStateService.activateIfReady(eventId, userId)).isPositive();
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
                .name("동시성테스터")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build());
    }

    @FunctionalInterface
    private interface ConcurrentAction {
        Object run(int index) throws Exception;
    }
}

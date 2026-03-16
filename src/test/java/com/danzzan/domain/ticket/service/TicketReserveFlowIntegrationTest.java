package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.controller.TicketController;
import com.danzzan.domain.ticket.dto.ResponseReserveTicketDto;
import com.danzzan.domain.ticket.exception.AlreadyReservedException;
import com.danzzan.domain.ticket.exception.EventNotOpenException;
import com.danzzan.domain.ticket.exception.EventSoldOutException;
import com.danzzan.domain.ticket.model.entity.QueueEntryStatus;
import com.danzzan.domain.ticket.model.entity.TicketQueueEntry;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TicketReserveFlowIntegrationTest {

    @Autowired
    private TicketController ticketController;

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueStateService queueStateService;

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

    @Autowired
    private StringRedisTemplate redisTemplate;

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
    void reserve_성공하면_DONE_projection과_userTicket이_저장된다() {
        User user = saveUser("32100011");
        FestivalEvent event = saveOpenEvent("예매 플로우 성공");
        prepareEvent(event, 5L);
        activateUser(event, user);

        ResponseEntity<ResponseReserveTicketDto> response = ticketController.reserveTicket(event.getId(), auth(user));

        TicketQueueEntry entry = findEntry(event, user);
        UserTicket savedTicket = userTicketRepository.findAll().get(0);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getQueueNumber()).isEqualTo(savedTicket.getTicketingOrder());
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.DONE);
        assertThat(entry.getReadyUntil()).isNull();
        assertThat(entry.getLeaseUntil()).isNull();
        assertThat(userTicketRepository.existsByUserIdAndEventId(user.getId(), event.getId())).isTrue();
        assertThat(queueStateService.activeCount(event.getId().toString())).isZero();
    }

    @Test
    void reserve_품절이면_FAILED_projection으로_마감된다() {
        User user = saveUser("32100012");
        FestivalEvent event = saveOpenEvent("예매 플로우 품절");
        prepareEvent(event, 1L);
        activateUser(event, user);
        redisTemplate.opsForValue().set(TicketRedisKeys.stockKey(event.getId().toString()), "0");

        assertThatThrownBy(() -> ticketController.reserveTicket(event.getId(), auth(user)))
                .isInstanceOf(EventSoldOutException.class);

        TicketQueueEntry entry = findEntry(event, user);
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.FAILED);
        assertThat(userTicketRepository.existsByUserIdAndEventId(user.getId(), event.getId())).isFalse();
        assertThat(queueStateService.activeCount(event.getId().toString())).isZero();
    }

    @Test
    void reserve_DB중복예매면_rollback후_FAILED_projection으로_마감된다() {
        User user = saveUser("32100013");
        FestivalEvent event = saveOpenEvent("예매 플로우 중복");
        prepareEvent(event, 1L);
        activateUser(event, user);
        userTicketRepository.save(UserTicket.builder()
                .user(user)
                .event(event)
                .ticketingOrder(99)
                .build());

        assertThatThrownBy(() -> ticketController.reserveTicket(event.getId(), auth(user)))
                .isInstanceOf(AlreadyReservedException.class);

        TicketQueueEntry entry = findEntry(event, user);
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.FAILED);
        assertThat(redisTemplate.opsForValue().get(TicketRedisKeys.stockKey(event.getId().toString()))).isEqualTo("1");
        assertThat(redisTemplate.hasKey(TicketRedisKeys.userKey(event.getId().toString(), user.getId().toString()))).isFalse();
        assertThat(redisTemplate.hasKey(TicketRedisKeys.statusKey(event.getId().toString(), user.getId().toString()))).isFalse();
        assertThat(queueStateService.activeCount(event.getId().toString())).isZero();
        assertThat(userTicketRepository.count()).isEqualTo(1);
    }

    @Test
    void reserve_활성lease가_만료되면_EXPIRED_projection으로_마감된다() {
        User user = saveUser("32100014");
        FestivalEvent event = saveOpenEvent("예매 플로우 lease 만료");
        prepareEvent(event, 1L);
        activateUser(event, user);

        String eventId = event.getId().toString();
        String userId = user.getId().toString();
        long expiredMs = System.currentTimeMillis() - 1_000L;
        redisTemplate.opsForHash().put(TicketRedisKeys.queueUserHashKey(eventId, userId), "activeUntil", String.valueOf(expiredMs));
        redisTemplate.opsForZSet().add(TicketRedisKeys.activeKey(eventId), userId, expiredMs);

        assertThatThrownBy(() -> ticketController.reserveTicket(event.getId(), auth(user)))
                .isInstanceOf(EventNotOpenException.class);

        TicketQueueEntry entry = findEntry(event, user);
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.EXPIRED);
        assertThat(queueStateService.activeCount(eventId)).isZero();
        assertThat(redisTemplate.hasKey(TicketRedisKeys.dedupKey(eventId, userId))).isFalse();
    }

    private void activateUser(FestivalEvent event, User user) {
        String eventId = event.getId().toString();
        String userId = user.getId().toString();
        queueService.enterQueue(eventId, userId);
        boolean admitted = queueStateService.admitNextWaitingUser(
                eventId,
                System.currentTimeMillis() + 60_000L,
                100
        );
        assertThat(admitted).isTrue();
        long activated = queueStateService.activateIfReady(eventId, userId);
        assertThat(activated).isPositive();
    }

    private void prepareEvent(FestivalEvent event, long stock) {
        eventIdsToClean.add(event.getId());
        ticketInitService.initStock(String.valueOf(event.getId()), stock);
    }

    private TicketQueueEntry findEntry(FestivalEvent event, User user) {
        return ticketQueueEntryRepository.findByEventIdAndUserId(event.getId(), user.getId())
                .orElseThrow();
    }

    private Authentication auth(User user) {
        return new TestingAuthenticationToken(user.getId(), null);
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
                .name("예매테스터")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build());
    }
}

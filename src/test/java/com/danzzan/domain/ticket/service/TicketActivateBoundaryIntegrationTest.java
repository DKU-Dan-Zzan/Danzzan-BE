package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.ticket.model.entity.QueueEntryStatus;
import com.danzzan.domain.ticket.model.entity.TicketQueueEntry;
import com.danzzan.domain.ticket.redis.TicketRedisKeys;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TicketActivateBoundaryIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueStateService queueStateService;

    @Autowired
    private TicketInitService ticketInitService;

    @Autowired
    private TicketQueueEntryRepository ticketQueueEntryRepository;

    @Autowired
    private FestivalEventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

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
    void activate_READYpermit이_만료되면_EXPIRED_projection으로_정리된다() {
        User user = saveUser("32100021");
        FestivalEvent event = saveOpenEvent("activate 경계 테스트");
        prepareEvent(event, 5L);

        String eventId = event.getId().toString();
        String userId = user.getId().toString();
        queueService.enterQueue(eventId, userId);
        boolean admitted = queueStateService.admitNextWaitingUser(eventId, System.currentTimeMillis() - 1_000L, 100);
        assertThat(admitted).isTrue();

        long activationResult = queueStateService.activateIfReady(eventId, userId);

        TicketQueueEntry entry = ticketQueueEntryRepository.findByEventIdAndUserId(event.getId(), user.getId())
                .orElseThrow();
        assertThat(activationResult).isIn(-1L, 0L);
        assertThat(entry.getStatus()).isEqualTo(QueueEntryStatus.EXPIRED);
        assertThat(queueStateService.activeCount(eventId)).isZero();
        assertThat(redisTemplate.opsForZSet().score(TicketRedisKeys.readyKey(eventId), userId)).isNull();
        assertThat(redisTemplate.hasKey(TicketRedisKeys.dedupKey(eventId, userId))).isFalse();
    }

    private void prepareEvent(FestivalEvent event, long stock) {
        eventIdsToClean.add(event.getId());
        ticketInitService.initStock(String.valueOf(event.getId()), stock);
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
                .name("활성화테스터")
                .college("공과대학")
                .major("컴퓨터공학과")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(UserRole.ROLE_USER)
                .build());
    }
}

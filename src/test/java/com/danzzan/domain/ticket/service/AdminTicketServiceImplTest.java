package com.danzzan.domain.ticket.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.ticket.model.entity.TicketStatus;
import com.danzzan.domain.ticket.model.entity.UserTicket;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import com.danzzan.domain.user.model.entity.AcademicStatus;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.model.entity.UserRole;
import com.danzzan.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTicketServiceImplTest {

    @Mock
    private UserTicketRepository userTicketRepository;

    @Mock
    private UserRepository userRepository;

    private AdminTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminTicketServiceImpl(userTicketRepository, userRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    void issueTicket_권리포기티켓은_팔찌지급할수없다() {
        User user = user(1L, UserRole.ROLE_USER);
        UserTicket ticket = UserTicket.cancelledByWithdrawal(
                user,
                event(10L),
                7,
                77L,
                LocalDateTime.of(2026, 5, 1, 12, 0)
        );
        when(userTicketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.issueTicket(10L, 100L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("권리포기");
    }

    private User user(Long id, UserRole role) {
        User user = User.builder()
                .studentId("321")
                .password("pw")
                .name("u")
                .college("c")
                .major("m")
                .academicStatus(AcademicStatus.ENROLLED)
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private FestivalEvent event(Long id) {
        FestivalEvent event = FestivalEvent.builder()
                .title("dan")
                .eventDate(LocalDate.of(2026, 5, 13))
                .ticketingStartTime(LocalDateTime.of(2026, 5, 13, 18, 0))
                .totalCapacity(100)
                .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}

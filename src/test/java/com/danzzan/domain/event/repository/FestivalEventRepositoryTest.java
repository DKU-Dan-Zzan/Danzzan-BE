package com.danzzan.domain.event.repository;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.festival.entity.FestivalTicketingRound;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class FestivalEventRepositoryTest {
    @Autowired FestivalEventRepository repository;
    @Autowired TestEntityManager em;

    @Test
    void excludesLegacyEventsAndOrdersConfiguredDays() {
        persist("지난 축제", LocalDate.of(2026,5,7));
        var third = persist("새 축제 DAY 3", LocalDate.of(2027,5,3));
        var second = persist("새 축제 DAY 2", LocalDate.of(2027,5,2));
        assertThat(repository.findConfiguredEvents()).isEmpty();
        link(third, 0);
        link(second, 1);
        em.flush();
        em.clear();
        assertThat(repository.findConfiguredEvents()).extracting(FestivalEvent::getTitle)
                .containsExactly("새 축제 DAY 2", "새 축제 DAY 3");
    }

    private FestivalEvent persist(String title, LocalDate date) {
        return em.persist(FestivalEvent.builder().title(title).eventDate(date)
                .ticketingStartTime(date.minusDays(2).atTime(18,0)).totalCapacity(100).build());
    }

    private void link(FestivalEvent event, int order) {
        var round = FestivalTicketingRound.create(event.getTicketingStartTime(), 100, event.getEventDate(), order);
        round.linkEvent(event.getId());
        em.persist(round);
    }
}

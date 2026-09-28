package com.danzzan.domain.festival.repository;

import com.danzzan.domain.festival.entity.FestivalTicketingRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalTicketingRoundRepository extends JpaRepository<FestivalTicketingRound, Long> {

    List<FestivalTicketingRound> findAllByOrderByDisplayOrderAsc();

    void deleteAllByIdIn(List<Long> ids);
}

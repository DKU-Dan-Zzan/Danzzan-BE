package com.danzzan.domain.timetable.repository;

import com.danzzan.domain.timetable.model.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, Integer> {
    boolean existsByName(String name);
}

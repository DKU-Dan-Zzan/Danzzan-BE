package com.danzzan.domain.timetable.repository;

import com.danzzan.domain.timetable.model.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistRepository extends JpaRepository<Artist, Integer> {
    boolean existsByName(String name);

    List<Artist> findTop50ByNameEnIsNullOrDescriptionEnIsNull();
}

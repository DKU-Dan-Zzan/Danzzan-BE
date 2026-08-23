package com.danzzan.domain.timetable.repository;

import com.danzzan.domain.timetable.model.entity.Artist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ArtistRepository extends JpaRepository<Artist, Integer> {
    boolean existsByName(String name);

    /**
     * 번역이 필요한 아티스트만 조회한다. 한국어 원문이 실제로 존재하는데(공백이 아닌데)
     * 영문이 비어 있는 행만 대상으로 한다 - description은 선택 입력이라 null일 수
     * 있으므로, 원문 자체가 없는 행까지 포함하면 영원히 채울 수 없는 행이 50건 창을
     * 영구히 차지해 스케줄러가 멈춘다.
     */
    @Query("""
        select a from Artist a
        where (a.nameEn is null and a.name is not null and a.name <> '')
           or (a.descriptionEn is null and a.description is not null and a.description <> '')
        order by a.id
    """)
    List<Artist> findNeedingTranslation(Pageable pageable);
}

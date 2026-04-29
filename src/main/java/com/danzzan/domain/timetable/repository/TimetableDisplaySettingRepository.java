package com.danzzan.domain.timetable.repository;

import com.danzzan.domain.timetable.model.entity.TimetableDisplaySetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableDisplaySettingRepository extends JpaRepository<TimetableDisplaySetting, Long> {
}

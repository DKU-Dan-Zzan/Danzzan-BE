package com.danzzan.domain.timetable.service;

import com.danzzan.domain.timetable.model.entity.TimetableDisplaySetting;
import com.danzzan.domain.timetable.repository.TimetableDisplaySettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableDisplaySettingService {

    private static final Long SINGLETON_ID = 1L;

    private final TimetableDisplaySettingRepository timetableDisplaySettingRepository;

    public boolean isComingSoonOverlayEnabled() {
        return timetableDisplaySettingRepository.findById(SINGLETON_ID)
                .map(TimetableDisplaySetting::isComingSoonOverlayEnabled)
                .orElse(false);
    }

    @Transactional
    public void updateComingSoonOverlayEnabled(boolean comingSoonOverlayEnabled) {
        TimetableDisplaySetting setting = timetableDisplaySettingRepository.findById(SINGLETON_ID)
                .orElseGet(() -> TimetableDisplaySetting.createDefault(false));
        setting.updateComingSoonOverlayEnabled(comingSoonOverlayEnabled);
        timetableDisplaySettingRepository.save(setting);
    }
}

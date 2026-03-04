package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.dto.request.UpdateEmergencyRequest;
import com.danzzan.domain.notice.dto.response.EmergencyNoticeResponse;
import com.danzzan.domain.notice.entity.EmergencyNotice;
import com.danzzan.domain.notice.repository.EmergencyNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmergencyNoticeService {

    private final EmergencyNoticeRepository emergencyNoticeRepository;

    @Transactional(readOnly = true)
    public EmergencyNoticeResponse get() {
        EmergencyNotice entity = emergencyNoticeRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::createDefault);
        return EmergencyNoticeResponse.from(entity);
    }

    @Transactional
    public EmergencyNoticeResponse update(UpdateEmergencyRequest request) {
        EmergencyNotice entity = emergencyNoticeRepository.findFirstByOrderByIdAsc()
                .orElseGet(this::createDefault);
        if (request.getMessage() != null) {
            entity.setMessage(request.getMessage());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        return EmergencyNoticeResponse.from(emergencyNoticeRepository.save(entity));
    }

    private EmergencyNotice createDefault() {
        EmergencyNotice entity = new EmergencyNotice();
        entity.setMessage("");
        entity.setIsActive(false);
        return emergencyNoticeRepository.save(entity);
    }
}

package com.danzzan.domain.notice.service;

import com.danzzan.domain.notice.dto.request.UpdateEmergencyRequest;
import com.danzzan.domain.notice.dto.response.EmergencyNoticeResponse;
import com.danzzan.domain.notice.entity.EmergencyNotice;
import com.danzzan.domain.notice.repository.EmergencyNoticeRepository;
import com.danzzan.infra.translation.FieldTranslationDecision;
import com.danzzan.infra.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmergencyNoticeService {

    private final EmergencyNoticeRepository emergencyNoticeRepository;
    private final TranslationService translationService;

    /**
     * 조회는 절대 저장하지 않는다.
     *
     * 예전에는 행이 없을 때 기본 행을 만들어 저장했는데, 이 메서드는 읽기 전용
     * 트랜잭션이라 "Connection is read-only" 로 500 이 났다. 긴급공지를 한 번도
     * 등록한 적 없는 상태에서 관리자 페이지를 여는 것만으로 터졌다.
     *
     * 행은 실제로 수정할 때(update) 만들면 충분하므로, 여기서는 저장하지 않은
     * 기본값을 그대로 돌려준다.
     */
    @Transactional(readOnly = true)
    public EmergencyNoticeResponse get() {
        EmergencyNotice entity = emergencyNoticeRepository.findFirstByOrderByIdAsc()
                .orElseGet(EmergencyNoticeService::emptyEmergencyNotice);
        return EmergencyNoticeResponse.from(entity);
    }

    @Transactional
    public EmergencyNoticeResponse update(UpdateEmergencyRequest request) {
        EmergencyNotice entity = emergencyNoticeRepository.findFirstByOrderByIdAsc()
                .orElseGet(EmergencyNoticeService::emptyEmergencyNotice);
        String previousMessage = entity.getMessage();
        if (request.getMessage() != null) {
            entity.setMessage(request.getMessage());
        }
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }

        // 긴급공지는 우천 중단이나 혼잡 경고처럼 외국인이 놓치면 곤란한 내용이 올라온다.
        // 한국어가 실제로 바뀐 경우에만 재번역하고, 관리자가 직접 쓴 영문은 지킨다.
        boolean koreanChanged = !java.util.Objects.equals(previousMessage, entity.getMessage());
        String autoEn = koreanChanged
                ? translationService.translate(entity.getMessage())
                : null;
        entity.setMessageEn(FieldTranslationDecision.decideEnglish(
                koreanChanged, request.getMessageEn(), entity.getMessageEn(), autoEn));
        if (FieldTranslationDecision.isSupplied(request.getMessageEn())) {
            entity.setEnIsManual(true);
        }

        return EmergencyNoticeResponse.from(emergencyNoticeRepository.save(entity));
    }

    /** 저장하지 않은 기본값. 조회 응답용이다. */
    private static EmergencyNotice emptyEmergencyNotice() {
        EmergencyNotice entity = new EmergencyNotice();
        entity.setMessage("");
        entity.setIsActive(false);
        return entity;
    }
}

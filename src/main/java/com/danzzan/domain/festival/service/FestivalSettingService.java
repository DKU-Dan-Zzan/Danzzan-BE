package com.danzzan.domain.festival.service;

import com.danzzan.domain.festival.dto.request.TicketingRoundRequest;
import com.danzzan.domain.festival.dto.request.UpdateFestivalSettingRequest;
import com.danzzan.domain.festival.dto.response.FestivalSettingResponse;
import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.entity.FestivalTicketingRound;
import com.danzzan.domain.festival.exception.InvalidFestivalSettingException;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.repository.FestivalTicketingRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FestivalSettingService {

    /** 운영 날짜는 최대 14일까지만 받는다. 잘못 입력한 연도 때문에 수천 개가 생기는 것을 막는다. */
    private static final int MAX_OPERATION_DAYS = 14;

    /** 아직 설정을 저장하지 않았을 때 쓰는 기본 학교명. */
    private static final String DEFAULT_SCHOOL_NAME = "단국대학교";

    private final FestivalSettingRepository festivalSettingRepository;
    private final FestivalTicketingRoundRepository festivalTicketingRoundRepository;
    private final TicketingAccessPolicy ticketingAccessPolicy;

    /**
     * 저장된 설정이 없으면 빈 설정을 내려준다. 프론트는 운영 날짜가 비어 있으면
     * 아직 등록 전으로 보고 기존 기본값을 쓴다.
     */
    public FestivalSettingResponse getSettings() {
        return festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)
                .map(setting -> FestivalSettingResponse.of(
                        setting,
                        buildOperationDates(setting.getStartDate(), setting.getEndDate()),
                        festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()
                ))
                .orElseGet(() -> new FestivalSettingResponse(
                        DEFAULT_SCHOOL_NAME, "", null, null, List.of(), false, List.of()
                ));
    }

    @Transactional
    public FestivalSettingResponse updateSettings(UpdateFestivalSettingRequest request) {
        List<LocalDate> operationDates = buildOperationDates(request.getStartDate(), request.getEndDate());
        validate(request, operationDates);

        FestivalSetting setting = festivalSettingRepository.findById(FestivalSetting.SINGLETON_ID)
                .orElseGet(() -> FestivalSetting.create(
                        request.getSchoolName(),
                        request.getFestivalName(),
                        request.getStartDate(),
                        request.getEndDate(),
                        request.isTicketingEnabled()
                ));
        setting.update(
                request.getSchoolName(),
                request.getFestivalName(),
                request.getStartDate(),
                request.getEndDate(),
                request.isTicketingEnabled()
        );
        festivalSettingRepository.save(setting);

        // 보낸 목록이 저장된 회차를 통째로 대신한다. 화면에서 지운 회차가 남지 않게 하기 위해서다.
        festivalTicketingRoundRepository.deleteAllInBatch();
        List<FestivalTicketingRound> rounds = toRounds(
                request.isTicketingEnabled() ? request.getTicketingRounds() : List.of()
        );
        festivalTicketingRoundRepository.saveAll(rounds);

        // 티켓팅 스위치를 방금 바꿨을 수 있으므로 캐시를 비워 즉시 반영한다.
        ticketingAccessPolicy.invalidate();

        return FestivalSettingResponse.of(setting, operationDates, rounds);
    }

    private void validate(UpdateFestivalSettingRequest request, List<LocalDate> operationDates) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new InvalidFestivalSettingException("운영 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (operationDates.size() > MAX_OPERATION_DAYS) {
            throw new InvalidFestivalSettingException("운영 기간은 최대 " + MAX_OPERATION_DAYS + "일까지 설정할 수 있습니다.");
        }
        if (!request.isTicketingEnabled()) {
            return;
        }
        for (TicketingRoundRequest round : request.getTicketingRounds()) {
            if (!operationDates.contains(round.getPerformanceDate())) {
                throw new InvalidFestivalSettingException("공연 날짜는 축제 운영 기간 안에 있어야 합니다.");
            }
        }
    }

    private List<FestivalTicketingRound> toRounds(List<TicketingRoundRequest> requests) {
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(index -> {
                    TicketingRoundRequest round = requests.get(index);
                    return FestivalTicketingRound.create(
                            round.getTicketingAt(),
                            round.getCapacity(),
                            round.getPerformanceDate(),
                            index
                    );
                })
                .toList();
    }

    /** 시작일부터 종료일까지 하루 간격으로 펼친다. 상한을 넘으면 검증에서 걸린다. */
    private List<LocalDate> buildOperationDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return List.of();
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return java.util.stream.LongStream.range(0, Math.min(days, MAX_OPERATION_DAYS + 1L))
                .mapToObj(startDate::plusDays)
                .toList();
    }
}

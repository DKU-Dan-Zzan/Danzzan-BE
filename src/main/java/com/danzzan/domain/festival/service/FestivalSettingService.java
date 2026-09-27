package com.danzzan.domain.festival.service;

import com.danzzan.domain.event.model.entity.FestivalEvent;
import com.danzzan.domain.event.model.entity.TicketingStatus;
import com.danzzan.domain.event.repository.FestivalEventRepository;
import com.danzzan.domain.festival.dto.request.TicketingRoundRequest;
import com.danzzan.domain.festival.dto.request.UpdateFestivalSettingRequest;
import com.danzzan.domain.festival.dto.response.FestivalSettingResponse;
import com.danzzan.domain.festival.dto.response.TicketingRoundResponse;
import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.entity.FestivalTicketingRound;
import com.danzzan.domain.festival.exception.InvalidFestivalSettingException;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.repository.FestivalTicketingRoundRepository;
import com.danzzan.domain.ticket.repository.UserTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

/**
 * 축제 운영 정보를 저장하고, 티켓팅 회차를 실제 티켓팅 이벤트로 만들어 둔다.
 *
 * 관리자가 설정에 적은 회차는 그 자체로는 아무 일도 하지 않는다. 대기열·티켓 발급·내
 * 티켓은 모두 festival_events 를 보기 때문에, 회차를 저장할 때 같은 내용의 이벤트를
 * 함께 만들어야 티켓팅이 실제로 열린다. 오픈 시각이 되면 기존 스케줄러가 알아서 연다.
 */
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
    private final FestivalEventRepository festivalEventRepository;
    private final UserTicketRepository userTicketRepository;
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
                        toResponses(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc())
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

        List<TicketingRoundResponse> rounds = syncTicketingRounds(request);

        // 티켓팅 스위치를 방금 바꿨을 수 있으므로 캐시를 비워 즉시 반영한다.
        ticketingAccessPolicy.invalidate();

        return FestivalSettingResponse.of(setting, operationDates, rounds);
    }

    /**
     * 보낸 회차 목록에 맞춰 회차와 티켓팅 이벤트를 함께 맞춘다.
     *
     * 티켓팅을 끄는 것은 "회차를 지운다"가 아니라 "지금은 열지 않는다"이다. 그래서 끌 때
     * 회차를 지우지 않는다. 지우면 이미 티켓을 받은 사람의 근거가 사라진다.
     */
    private List<TicketingRoundResponse> syncTicketingRounds(UpdateFestivalSettingRequest request) {
        if (!request.isTicketingEnabled()) {
            return toResponses(festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc());
        }

        Map<Long, FestivalTicketingRound> saved = new LinkedHashMap<>();
        festivalTicketingRoundRepository.findAllByOrderByDisplayOrderAsc()
                .forEach(round -> saved.put(round.getId(), round));

        List<FestivalTicketingRound> result = new ArrayList<>();
        List<TicketingRoundRequest> requested = request.getTicketingRounds();

        for (int index = 0; index < requested.size(); index++) {
            TicketingRoundRequest incoming = requested.get(index);
            FestivalTicketingRound existing = incoming.getId() == null ? null : saved.remove(incoming.getId());

            if (existing == null) {
                result.add(createRound(request.getFestivalName(), incoming, index));
                continue;
            }
            result.add(updateRound(request.getFestivalName(), existing, incoming, index));
        }

        // 화면에서 빠진 회차는 지운다. 단 이미 티켓팅이 시작된 회차는 지울 수 없다.
        for (FestivalTicketingRound removed : saved.values()) {
            if (isLocked(removed)) {
                throw new InvalidFestivalSettingException(
                        "이미 티켓팅이 시작된 회차는 삭제할 수 없습니다. 티켓팅을 끄려면 티켓팅 여부를 OFF 로 저장해 주세요.");
            }
            deleteEventOf(removed);
            festivalTicketingRoundRepository.delete(removed);
        }

        return toResponses(result);
    }

    private FestivalTicketingRound createRound(String festivalName, TicketingRoundRequest incoming, int index) {
        FestivalTicketingRound round = FestivalTicketingRound.create(
                incoming.getTicketingAt(), incoming.getCapacity(), incoming.getPerformanceDate(), index);

        FestivalEvent event = festivalEventRepository.save(FestivalEvent.builder()
                .title(eventTitle(festivalName, index))
                .eventDate(incoming.getPerformanceDate())
                .ticketingStartTime(incoming.getTicketingAt())
                .ticketingStatus(TicketingStatus.READY)
                .totalCapacity(incoming.getCapacity())
                .build());

        round.linkEvent(event.getId());
        return festivalTicketingRoundRepository.save(round);
    }

    private FestivalTicketingRound updateRound(
            String festivalName,
            FestivalTicketingRound existing,
            TicketingRoundRequest incoming,
            int index
    ) {
        boolean changed = !existing.getTicketingAt().equals(incoming.getTicketingAt())
                || existing.getCapacity() != incoming.getCapacity()
                || !existing.getPerformanceDate().equals(incoming.getPerformanceDate());

        if (changed && isLocked(existing)) {
            throw new InvalidFestivalSettingException(
                    "이미 티켓팅이 시작된 회차는 수정할 수 없습니다.");
        }

        existing.update(incoming.getTicketingAt(), incoming.getCapacity(), incoming.getPerformanceDate(), index);

        findEventOf(existing).ifPresentOrElse(
                event -> event.updateBeforeOpen(
                        eventTitle(festivalName, index),
                        incoming.getPerformanceDate(),
                        incoming.getTicketingAt(),
                        incoming.getCapacity()
                ),
                // 이벤트가 없던 회차(연결 전에 저장된 회차)는 이제 만들어 준다.
                () -> {
                    FestivalEvent event = festivalEventRepository.save(FestivalEvent.builder()
                            .title(eventTitle(festivalName, index))
                            .eventDate(incoming.getPerformanceDate())
                            .ticketingStartTime(incoming.getTicketingAt())
                            .ticketingStatus(TicketingStatus.READY)
                            .totalCapacity(incoming.getCapacity())
                            .build());
                    existing.linkEvent(event.getId());
                }
        );

        return festivalTicketingRoundRepository.save(existing);
    }

    /**
     * 손대면 안 되는 회차인지 본다. 티켓팅이 이미 열렸거나(오픈/마감) 티켓이 한 장이라도
     * 나갔으면 잠근다.
     */
    private boolean isLocked(FestivalTicketingRound round) {
        Optional<FestivalEvent> event = findEventOf(round);
        if (event.isEmpty()) {
            return false;
        }
        if (event.get().getTicketingStatus() != TicketingStatus.READY) {
            return true;
        }
        return userTicketRepository.countByEventId(event.get().getId()) > 0;
    }

    private Optional<FestivalEvent> findEventOf(FestivalTicketingRound round) {
        return round.getEventId() == null
                ? Optional.empty()
                : festivalEventRepository.findById(round.getEventId());
    }

    private void deleteEventOf(FestivalTicketingRound round) {
        findEventOf(round).ifPresent(festivalEventRepository::delete);
    }

    private String eventTitle(String festivalName, int index) {
        String name = (festivalName == null || festivalName.isBlank()) ? "축제" : festivalName.trim();
        return name + " " + (index + 1) + "회차";
    }

    private List<TicketingRoundResponse> toResponses(List<FestivalTicketingRound> rounds) {
        Map<Long, Boolean> lockedCache = new HashMap<>();
        return rounds.stream()
                .map(round -> TicketingRoundResponse.from(
                        round,
                        lockedCache.computeIfAbsent(round.getId(), id -> isLocked(round))
                ))
                .toList();
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

    /** 시작일부터 종료일까지 하루 간격으로 펼친다. 상한을 넘으면 검증에서 걸린다. */
    private List<LocalDate> buildOperationDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return List.of();
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return LongStream.range(0, Math.min(days, MAX_OPERATION_DAYS + 1L))
                .mapToObj(startDate::plusDays)
                .toList();
    }
}

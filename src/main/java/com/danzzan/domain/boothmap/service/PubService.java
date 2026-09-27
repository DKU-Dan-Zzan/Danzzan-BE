package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.PubDetailResponse;
import com.danzzan.domain.boothmap.model.dto.PubSummaryResponse;
import com.danzzan.domain.boothmap.model.entity.Pub;
import com.danzzan.domain.boothmap.model.entity.PubImage;
import com.danzzan.domain.boothmap.model.entity.PubOperation;
import com.danzzan.domain.boothmap.repository.PubImageRepository;
import com.danzzan.domain.boothmap.repository.PubOperationRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
import com.danzzan.domain.boothmap.util.ThumbnailUrlResolver;
import com.danzzan.infra.translation.LocalizedText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PubService {
    private final PubRepository pubRepository;
    private final PubImageRepository pubImageRepository;
    private final PubOperationRepository pubOperationRepository;

    /**
     * 그 날짜에 운영하는 주점이 하나도 없으면 빈 목록을 준다.
     *
     * 예전에는 예외를 던져 404 가 나갔고, 축제 운영 날짜를 옮기면(=아직 주점을 등록하지
     * 않은 날짜) 부스맵 화면이 통째로 "정보를 불러올 수 없습니다" 가 됐다. 목록 조회에서
     * 결과가 없는 것은 오류가 아니다. 부스 목록도 빈 목록으로 응답한다.
     */
    public List<PubSummaryResponse> getPubs(LocalDate operationDate, boolean english) {
        PubOperation pubOperation = findPubOperation(operationDate).orElse(null);
        if (pubOperation == null) {
            return List.of();
        }

        List<Pub> pubs = pubRepository.findAllVisibleByPubOperationIdWithCollegeAndImages(pubOperation.getId());

        String startTime = formatTime(pubOperation.getStartTime());
        String endTime = formatTime(pubOperation.getEndTime());

        return pubs.stream()
                .map(pub -> {
                    String mainImageUrl = pub.getImages().stream()
                            .filter(PubImage::isMain)
                            .map(PubImage::getImageUrl)
                            .findFirst()
                            .orElse(null);

                    return new PubSummaryResponse(
                            pub.getId(),
                            LocalizedText.pick(english, pub.getName(), pub.getNameEn()),
                            LocalizedText.pick(english, pub.getIntro(), pub.getIntroEn()),
                            LocalizedText.pick(english, pub.getDepartment(), pub.getDepartmentEn()),
                            pub.getCollege().getId(),
                            LocalizedText.pick(english, pub.getCollege().getName(), pub.getCollege().getNameEn()),
                            mainImageUrl,
                            ThumbnailUrlResolver.toThumbnailUrl(mainImageUrl),
                            startTime,
                            endTime
                    );
                })
                .toList();
    }

    public PubDetailResponse getPubDetail(Long pubId, LocalDate operationDate, boolean english) {
        PubOperation pubOperation = resolvePubOperation(operationDate);
        Pub pub = pubRepository.findByIdWithCollege(pubId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주점을 찾을 수 없습니다. id=" + pubId));

        List<String> imageUrls = pubImageRepository.findByPubId(pubId)
                .stream()
                .map(PubImage::getImageUrl)
                .toList();
        List<String> thumbnailImageUrls = imageUrls.stream()
                .map(ThumbnailUrlResolver::toThumbnailUrl)
                .toList();

        return new PubDetailResponse(
                pub.getId(),
                LocalizedText.pick(english, pub.getName(), pub.getNameEn()),
                LocalizedText.pick(english, pub.getIntro(), pub.getIntroEn()),
                LocalizedText.pick(english, pub.getDescription(), pub.getDescriptionEn()),
                LocalizedText.pick(english, pub.getDepartment(), pub.getDepartmentEn()),
                LocalizedText.pick(english, pub.getCollege().getName(), pub.getCollege().getNameEn()),
                pub.getInstagram(),
                imageUrls,
                thumbnailImageUrls,
                formatTime(pubOperation.getStartTime()),
                formatTime(pubOperation.getEndTime())
        );
    }

    /** 주점 하나를 펼쳐 보는 상세 조회는 운영 정보가 없으면 404 가 맞다. */
    private PubOperation resolvePubOperation(LocalDate operationDate) {
        return findPubOperation(operationDate)
                .orElseThrow(() -> new IllegalArgumentException(
                        operationDate == null
                                ? "주점 운영 정보가 없습니다."
                                : "해당 날짜의 주점 운영 정보가 없습니다."));
    }

    private java.util.Optional<PubOperation> findPubOperation(LocalDate operationDate) {
        return (operationDate == null)
                ? pubOperationRepository.findFirstByOrderByOperationDateAsc()
                : pubOperationRepository.findByOperationDate(operationDate);
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }
}

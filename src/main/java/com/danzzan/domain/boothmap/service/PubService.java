package com.danzzan.domain.boothmap.service;

import com.danzzan.domain.boothmap.model.dto.PubDetailResponse;
import com.danzzan.domain.boothmap.model.dto.PubSummaryResponse;
import com.danzzan.domain.boothmap.model.entity.Pub;
import com.danzzan.domain.boothmap.model.entity.PubImage;
import com.danzzan.domain.boothmap.model.entity.PubOperation;
import com.danzzan.domain.boothmap.repository.PubImageRepository;
import com.danzzan.domain.boothmap.repository.PubOperationRepository;
import com.danzzan.domain.boothmap.repository.PubRepository;
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

    public List<PubSummaryResponse> getPubs(LocalDate operationDate) {
        List<Pub> pubs = pubRepository.findAllWithCollegeAndImages();

        PubOperation pubOperation = pubOperationRepository.findByOperationDate(operationDate)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 주점 운영정보가 없습니다."));

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
                            pub.getName(),
                            pub.getIntro(),
                            pub.getDepartment(),
                            pub.getCollege().getId(),
                            pub.getCollege().getName(),
                            mainImageUrl,
                            startTime,
                            endTime
                    );
                })
                .toList();
    }

    public PubDetailResponse getPubDetail(Long pubId, LocalDate operationDate) {
        Pub pub = pubRepository.findByIdWithCollege(pubId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주점을 찾을 수 없습니다. id=" + pubId));

        PubOperation pubOperation = pubOperationRepository.findByOperationDate(operationDate)
                .orElseThrow(() -> new IllegalArgumentException("해당 날짜의 주점 운영정보가 없습니다."));

        List<String> imageUrls = pubImageRepository.findByPubId(pubId)
                .stream()
                .map(PubImage::getImageUrl)
                .toList();

        return new PubDetailResponse(
                pub.getId(),
                pub.getName(),
                pub.getIntro(),
                pub.getDescription(),
                pub.getDepartment(),
                pub.getCollege().getName(),
                pub.getInstagram(),
                imageUrls,
                formatTime(pubOperation.getStartTime()),
                formatTime(pubOperation.getEndTime())
        );
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }
}
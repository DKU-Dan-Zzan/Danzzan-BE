package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import com.danzzan.domain.advertisement.dto.CreateAdvertisementRequest;
import com.danzzan.domain.advertisement.dto.request.PresignAdvertisementImageRequest;
import com.danzzan.domain.advertisement.dto.response.AdvertisementImagePresignResponse;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAdvertisementService {

    private final AdvertisementRepository advertisementRepository;
    private final S3PresignService s3PresignService;

    /**
     * 광고 이미지 업로드용 Presigned PUT URL 발급.
     */
    public AdvertisementImagePresignResponse presignAdImage(PresignAdvertisementImageRequest request) {
        S3PresignedPutResult result = s3PresignService.presignPutAdImage(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        return AdvertisementImagePresignResponse.from(result);
    }

    /**
     * 새 광고를 생성하면서, 같은 placement의 기존 활성 광고는 모두 비활성화합니다.
     */
    @Transactional
    public AdvertisementResponse createOrReplace(CreateAdvertisementRequest request) {
        AdvertisementPlacement placement = request.getPlacement();

        List<Advertisement> existing = advertisementRepository.findByPlacementOrderByCreatedAtDesc(placement);
        for (Advertisement ad : existing) {
            if (ad.getDeletedAt() != null) {
                continue;
            }
            if (Boolean.TRUE.equals(ad.getIsActive())) {
                ad.setIsActive(false);
            }
        }

        Advertisement ad = new Advertisement();
        ad.setTitle(request.getTitle());
        ad.setImageUrl(request.getImageUrl());
        ad.setPlacement(placement);
        ad.setIsActive(true);
        ad.setDeletedAt(null);

        return AdvertisementResponse.from(advertisementRepository.save(ad));
    }

    /**
     * placement 슬롯에서 가장 최근(삭제되지 않은) 광고만 활성화합니다.
     */
    @Transactional
    public AdvertisementResponse activatePlacement(AdvertisementPlacement placement) {
        Advertisement latest = advertisementRepository
                .findFirstByPlacementAndDeletedAtIsNullOrderByCreatedAtDesc(placement)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "해당 위치에 등록된 광고가 없습니다."
                ));

        List<Advertisement> all = advertisementRepository.findByPlacementOrderByCreatedAtDesc(placement);
        for (Advertisement row : all) {
            if (row.getDeletedAt() != null) {
                continue;
            }
            row.setIsActive(row.getId().equals(latest.getId()));
        }
        advertisementRepository.saveAll(all);
        return AdvertisementResponse.from(latest);
    }

    /**
     * placement에 남아 있는 비삭제 광고를 모두 비활성화합니다.
     */
    @Transactional
    public void deactivatePlacement(AdvertisementPlacement placement) {
        List<Advertisement> all = advertisementRepository.findByPlacementOrderByCreatedAtDesc(placement);
        for (Advertisement row : all) {
            if (row.getDeletedAt() != null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsActive())) {
                row.setIsActive(false);
            }
        }
        advertisementRepository.saveAll(all);
    }

    /**
     * 현재 노출 중인 광고를 소프트 삭제합니다(DB 행은 유지).
     */
    @Transactional
    public void softDeleteByPlacement(AdvertisementPlacement placement) {
        advertisementRepository
                .findFirstByPlacementAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(placement)
                .ifPresent(ad -> {
                    ad.setIsActive(false);
                    ad.setDeletedAt(LocalDateTime.now());
                });
    }

    /**
     * 삭제되지 않은 모든 광고를 최신순으로 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<AdvertisementResponse> getAllAds() {
        return advertisementRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc()
                .stream()
                .map(AdvertisementResponse::from)
                .toList();
    }

    /**
     * ID로 특정 광고를 소프트 삭제합니다.
     */
    @Transactional
    public void softDeleteById(Long id) {
        advertisementRepository.findById(id)
                .filter(ad -> ad.getDeletedAt() == null)
                .ifPresent(ad -> {
                    ad.setIsActive(false);
                    ad.setDeletedAt(LocalDateTime.now());
                });
    }

}

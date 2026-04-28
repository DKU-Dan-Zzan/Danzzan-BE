package com.danzzan.domain.timetable.service;

import com.danzzan.domain.timetable.dto.admin.request.CreateArtistRequest;
import com.danzzan.domain.timetable.dto.admin.request.PresignArtistImageRequest;
import com.danzzan.domain.timetable.dto.admin.request.UpdateArtistRequest;
import com.danzzan.domain.timetable.dto.admin.response.AdminArtistResponse;
import com.danzzan.domain.timetable.dto.admin.response.ArtistImagePresignResponse;
import com.danzzan.domain.timetable.model.entity.Artist;
import com.danzzan.domain.timetable.repository.ArtistRepository;
import com.danzzan.domain.timetable.repository.PerformanceRepository;
import com.danzzan.infra.s3.S3PresignService;
import com.danzzan.infra.s3.S3PresignedPutResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminArtistService {

    private final ArtistRepository artistRepository;
    private final PerformanceRepository performanceRepository;
    private final S3PresignService s3PresignService;

    @Transactional(readOnly = true)
    public List<AdminArtistResponse> getArtists() {
        return artistRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(AdminArtistResponse::from)
                .toList();
    }

    @Transactional
    public AdminArtistResponse createArtist(CreateArtistRequest request) {
        String name = request.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아티스트 이름을 입력해 주세요.");
        }
        Artist artist = Artist.create(name, trimToNull(request.getDescription()), trimToNull(request.getImageUrl()));
        Artist saved = artistRepository.save(artist);
        return AdminArtistResponse.from(saved);
    }

    @Transactional
    public AdminArtistResponse updateArtist(Integer artistId, UpdateArtistRequest request) {
        Artist artist = findArtist(artistId);

        String trimmedName = request.getName() != null ? request.getName().trim() : null;
        if (trimmedName != null && trimmedName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "아티스트 이름을 입력해 주세요.");
        }
        String description = request.getDescription();
        artist.updateProfile(trimmedName, description);

        if (request.getImageUrl() != null) {
            String trimmed = request.getImageUrl().trim();
            artist.changeImageUrl(trimmed.isEmpty() ? null : trimmed);
        }

        return AdminArtistResponse.from(artist);
    }

    @Transactional
    public void deleteArtist(Integer artistId) {
        if (!artistRepository.existsById(artistId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "아티스트를 찾을 수 없습니다.");
        }
        if (performanceRepository.existsByArtistId(artistId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이 아티스트가 등록된 공연이 있어 삭제할 수 없습니다. 공연을 먼저 삭제해 주세요."
            );
        }
        artistRepository.deleteById(artistId);
    }

    public ArtistImagePresignResponse presignArtistImage(Integer artistId, PresignArtistImageRequest request) {
        if (!artistRepository.existsById(artistId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "아티스트를 찾을 수 없습니다.");
        }
        S3PresignedPutResult result = s3PresignService.presignPutArtistImage(
                artistId,
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        return ArtistImagePresignResponse.from(result);
    }

    private Artist findArtist(Integer artistId) {
        return artistRepository.findById(artistId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "아티스트를 찾을 수 없습니다."
                ));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package com.danzzan.domain.timetable.controller;

import com.danzzan.domain.timetable.dto.admin.request.CreateArtistRequest;
import com.danzzan.domain.timetable.dto.admin.request.PresignArtistImageRequest;
import com.danzzan.domain.timetable.dto.admin.request.UpdateArtistRequest;
import com.danzzan.domain.timetable.dto.admin.response.AdminArtistResponse;
import com.danzzan.domain.timetable.dto.admin.response.ArtistImagePresignResponse;
import com.danzzan.domain.timetable.service.AdminArtistService;
import com.danzzan.infra.s3.S3UploadResult;
import com.danzzan.infra.s3.S3Uploader;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/timetable/artists")
@RequiredArgsConstructor
@Tag(name = "관리자 타임테이블 - 아티스트", description = "관리자 아티스트 CRUD 및 이미지 업로드 API")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@userAdminAuthorizationService.hasOperationsRole(authentication)")
public class AdminArtistController {

    private final AdminArtistService adminArtistService;
    private final S3Uploader s3Uploader;

    @GetMapping
    public ResponseEntity<List<AdminArtistResponse>> getArtists() {
        return ResponseEntity.ok(adminArtistService.getArtists());
    }

    @PostMapping
    public ResponseEntity<AdminArtistResponse> createArtist(
            @Valid @RequestBody CreateArtistRequest request
    ) {
        return ResponseEntity.ok(adminArtistService.createArtist(request));
    }

    @PatchMapping("/{artistId}")
    public ResponseEntity<AdminArtistResponse> updateArtist(
            @PathVariable Integer artistId,
            @Valid @RequestBody UpdateArtistRequest request
    ) {
        return ResponseEntity.ok(adminArtistService.updateArtist(artistId, request));
    }

    @DeleteMapping("/{artistId}")
    public ResponseEntity<Void> deleteArtist(@PathVariable Integer artistId) {
        adminArtistService.deleteArtist(artistId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{artistId}/images/presign")
    public ResponseEntity<ArtistImagePresignResponse> presignArtistImage(
            @PathVariable Integer artistId,
            @Valid @RequestBody PresignArtistImageRequest request
    ) {
        return ResponseEntity.ok(adminArtistService.presignArtistImage(artistId, request));
    }

    @PostMapping("/{artistId}/images/upload")
    public ResponseEntity<Map<String, String>> uploadArtistImage(
            @PathVariable Long artistId,
            @RequestParam("file") MultipartFile file
    ) {
        S3UploadResult result = s3Uploader.uploadArtistImage(artistId, file);
        return ResponseEntity.ok(Map.of("imageUrl", result.url(), "key", result.key()));
    }
}

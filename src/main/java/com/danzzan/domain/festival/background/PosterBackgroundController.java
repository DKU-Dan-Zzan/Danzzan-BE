package com.danzzan.domain.festival.background;

import com.danzzan.infra.image.json.ImageReferenceDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/festival/ticketing-background")
public class PosterBackgroundController {
    private final PosterBackgroundService service;
    public record Request(@NotNull @Pattern(regexp = "ticket|closed") String target,
                          @Size(max = 2048)
                          @Pattern(regexp = "(?:https?://|azure:images/)[^\\s]+")
                          @JsonDeserialize(using = ImageReferenceDeserializer.class) String sourcePosterUrl) {}

    @PostMapping("/recommend")
    @PreAuthorize("@userAdminAuthorizationService.hasTicketingRole(authentication)")
    public PosterBackgroundService.Recommendation recommend(@Valid @RequestBody Request request) {
        return request.sourcePosterUrl() == null ? service.recommend(request.target())
                : service.recommend(request.target(), request.sourcePosterUrl());
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> invalidPoster(
            org.springframework.web.server.ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode()).body(java.util.Map.of(
                "status", exception.getStatusCode().value(),
                "message", exception.getReason() == null ? "공식 포스터를 불러올 수 없습니다." : exception.getReason()));
    }
}

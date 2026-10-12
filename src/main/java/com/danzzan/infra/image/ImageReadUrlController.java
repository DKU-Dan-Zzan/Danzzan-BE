package com.danzzan.infra.image;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "image.storage", name = "provider", havingValue = "azure")
public class ImageReadUrlController {
    private final ImageReadUrlService imageReadUrlService;

    @PostMapping("/images/read-url")
    public ResponseEntity<ImageReadUrlResponse> renew(@Valid @RequestBody ImageReadUrlRequest request,
                                                        Authentication authentication) {
        String freshUrl = imageReadUrlService.renew(request.imageUrl(), authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ImageReadUrlResponse(freshUrl));
    }
}

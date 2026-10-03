package com.danzzan.domain.festival.service;

import com.danzzan.domain.festival.dto.response.FestivalBrandingResponse;
import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.infra.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FestivalBrandingService {
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final FestivalSettingRepository settings;
    private final S3Uploader uploader;

    public FestivalBrandingResponse get(Long festivalSettingId) {
        return FestivalBrandingResponse.of(find(festivalSettingId));
    }

    @Transactional
    public FestivalBrandingResponse uploadPoster(Long festivalSettingId, MultipartFile file) {
        FestivalSetting setting = find(festivalSettingId);
        validate(file);
        String url = uploader.uploadFestivalPoster(festivalSettingId, file).url();
        setting.updatePosterImageUrl(url);
        return FestivalBrandingResponse.of(setting);
    }

    @Transactional
    public FestivalBrandingResponse uploadLogo(Long festivalSettingId, MultipartFile file) {
        FestivalSetting setting = find(festivalSettingId);
        validate(file);
        String url = uploader.uploadFestivalLogo(festivalSettingId, file).url();
        setting.updateLogoImageUrl(url);
        return FestivalBrandingResponse.of(setting);
    }

    private FestivalSetting find(Long id) {
        return settings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Festival setting not found"));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES
                || !ALLOWED_TYPES.contains(file.getContentType())) throw invalidImage();
        try (var input = file.getInputStream(); var imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) throw invalidImage();
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                reader.setInput(imageInput);
                String format = reader.getFormatName();
                if (!("image/png".equals(file.getContentType()) && "png".equalsIgnoreCase(format))
                        && !("image/jpeg".equals(file.getContentType()) && "jpeg".equalsIgnoreCase(format))) throw invalidImage();
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > 40_000_000L || reader.read(0) == null) throw invalidImage();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) { throw invalidImage(); }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid JPEG or PNG image up to 10 MB and 40 million pixels");
    }
}

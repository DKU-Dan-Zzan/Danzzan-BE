package com.danzzan.domain.home.service;

import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.home.model.dto.AdminHomeImageDto;
import com.danzzan.domain.home.model.dto.HomeImageFirstReplaceRequest;
import com.danzzan.domain.home.repository.HomeImageRepository;
import com.danzzan.infra.image.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Transactional(readOnly = true)
public class AdminHomeImageService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final FestivalSettingRepository settings;
    private final HomeImageRepository images;
    private final ImageStorage storage;
    private final HomeImageWriter writer;

    public AdminHomeImageService(FestivalSettingRepository settings, HomeImageRepository images, ImageStorage storage) {
        this(settings, images, storage, new HomeImageWriter(settings, images));
    }

    public record StagedImage(String url) {}

    public List<AdminHomeImageDto> list(Long festivalSettingId) {
        requireFestival(festivalSettingId);
        return images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(festivalSettingId)
                .stream().map(AdminHomeImageDto::from).toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StagedImage stage(Long festivalSettingId, MultipartFile file) {
        requireFestival(festivalSettingId);
        validate(file);
        return new StagedImage(storage.uploadHomeImage(festivalSettingId, file));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminHomeImageDto upload(Long festivalSettingId, MultipartFile file) {
        String url = stage(festivalSettingId, file).url();
        return writer.append(festivalSettingId, url);
    }

    /** Verifies the staged file before the writer locks and atomically replaces only banner zero. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminHomeImageDto replaceFirst(Long festivalSettingId, HomeImageFirstReplaceRequest request) {
        requireFestival(festivalSettingId);
        storage.validateHomeImage(festivalSettingId, request.imageUrl());
        return writer.replaceFirst(festivalSettingId, request.imageUrl(), request.expectedFirstImageId(),
                request.expectedFirstImageUrl(), request.sourcePosterUrl());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void delete(Long festivalSettingId, Long imageId) {
        writer.delete(festivalSettingId, imageId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<AdminHomeImageDto> reorder(Long festivalSettingId, List<Long> imageIds) {
        return writer.reorder(festivalSettingId, imageIds);
    }

    private void requireFestival(Long id) {
        if (id == null || !settings.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Festival setting not found");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES || !ALLOWED_TYPES.contains(file.getContentType())) throw invalidImage();
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
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid JPEG or PNG image up to 5 MB and 40 million pixels");
    }
}

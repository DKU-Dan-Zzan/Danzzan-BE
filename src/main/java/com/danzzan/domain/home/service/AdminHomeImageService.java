package com.danzzan.domain.home.service;

import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.home.model.dto.AdminHomeImageDto;
import com.danzzan.domain.home.model.entity.HomeImage;
import com.danzzan.domain.home.repository.HomeImageRepository;
import com.danzzan.infra.image.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminHomeImageService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");

    private final FestivalSettingRepository settings;
    private final HomeImageRepository images;
    private final ImageStorage storage;

    public List<AdminHomeImageDto> list(Long festivalSettingId) {
        requireFestival(festivalSettingId);
        return images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(festivalSettingId)
                .stream().map(AdminHomeImageDto::from).toList();
    }

    @Transactional
    public AdminHomeImageDto upload(Long festivalSettingId, MultipartFile file) {
        requireFestival(festivalSettingId);
        validate(file);
        List<HomeImage> existing = images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(festivalSettingId);
        int nextOrder = existing.stream().mapToInt(HomeImage::getDisplayOrder).max().orElse(-1) + 1;
        String url = storage.uploadHomeImage(festivalSettingId, file);
        return AdminHomeImageDto.from(images.save(new HomeImage(url, festivalSettingId, nextOrder)));
    }

    @Transactional
    public void delete(Long festivalSettingId, Long imageId) {
        requireFestival(festivalSettingId);
        HomeImage image = images.findByIdAndFestivalSettingId(imageId, festivalSettingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Home image not found"));
        images.delete(image);
        // Keep stored image files intact; other URLs may still reference them.
    }

    @Transactional
    public List<AdminHomeImageDto> reorder(Long festivalSettingId, List<Long> imageIds) {
        requireFestival(festivalSettingId);
        List<HomeImage> existing = images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(festivalSettingId);
        Map<Long, HomeImage> byId = existing.stream().collect(Collectors.toMap(HomeImage::getId, Function.identity()));
        if (imageIds == null || imageIds.size() != existing.size() || imageIds.stream().anyMatch(id -> id == null || !byId.containsKey(id))
                || imageIds.stream().distinct().count() != imageIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image order must contain every image of this festival exactly once");
        }
        for (int index = 0; index < imageIds.size(); index++) byId.get(imageIds.get(index)).updateDisplayOrder(index);
        return imageIds.stream().map(id -> AdminHomeImageDto.from(byId.get(id))).toList();
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

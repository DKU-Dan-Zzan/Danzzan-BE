package com.danzzan.domain.home.service;

import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.home.model.dto.AdminHomeImageDto;
import com.danzzan.domain.home.model.entity.HomeImage;
import com.danzzan.domain.home.repository.HomeImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** All home-image publication shares the festival lock with theme bundle activation. */
@Service
@RequiredArgsConstructor
@Transactional
public class HomeImageWriter {
    private final FestivalSettingRepository settings;
    private final HomeImageRepository images;

    public AdminHomeImageDto append(Long festivalId, String url) {
        lock(festivalId);
        int order = images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(festivalId).stream()
                .mapToInt(HomeImage::getDisplayOrder).max().orElse(-1) + 1;
        return AdminHomeImageDto.from(images.save(new HomeImage(url, festivalId, order)));
    }

    public void delete(Long festivalId, Long imageId) {
        lock(festivalId);
        images.delete(images.findByIdAndFestivalSettingId(imageId, festivalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Home image not found")));
        // Stored files can still be referenced elsewhere; cleanup is a separate maintenance task.
    }

    public List<AdminHomeImageDto> reorder(Long festivalId, List<Long> imageIds) {
        lock(festivalId);
        var existing = images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(festivalId);
        var byId = existing.stream().collect(Collectors.toMap(HomeImage::getId, Function.identity()));
        if (imageIds == null || imageIds.size() != existing.size()
                || imageIds.stream().anyMatch(id -> id == null || !byId.containsKey(id))
                || imageIds.stream().distinct().count() != imageIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image order must contain every image of this festival exactly once");
        }
        for (int index = 0; index < imageIds.size(); index++) byId.get(imageIds.get(index)).updateDisplayOrder(index);
        return imageIds.stream().map(id -> AdminHomeImageDto.from(byId.get(id))).toList();
    }

    /** Joins the caller's bundle transaction; the comparison and mutation both run under its row lock. */
    public void replaceFirst(Long festivalId, String url, Long expectedId, String expectedUrl) {
        lock(festivalId);
        var existing = images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(festivalId);
        HomeImage first = existing.isEmpty() ? null : existing.get(0);
        if (!Objects.equals(first == null ? null : first.getId(), expectedId)
                || !Objects.equals(first == null ? null : first.getImageUrl(), expectedUrl)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "홈 이미지가 변경되었습니다. 다시 미리보기해 주세요.");
        }
        if (first == null) images.save(new HomeImage(url, festivalId, 0));
        else first.updateImageUrl(url);
    }

    private void lock(Long id) {
        settings.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Festival setting not found"));
    }
}

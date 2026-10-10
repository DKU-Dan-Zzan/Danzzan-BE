package com.danzzan.domain.home;

import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.home.model.entity.HomeImage;
import com.danzzan.domain.home.repository.HomeImageRepository;
import com.danzzan.domain.home.service.AdminHomeImageService;
import com.danzzan.infra.image.ImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminHomeImageServiceTest {
    private final FestivalSettingRepository settings = mock(FestivalSettingRepository.class);
    private final HomeImageRepository images = mock(HomeImageRepository.class);
    private final ImageStorage storage = mock(ImageStorage.class);
    private final AdminHomeImageService service = new AdminHomeImageService(settings, images, storage);

    private HomeImage image(long id, long festival, int order) {
        HomeImage result = new HomeImage("https://cdn/" + id + ".png", festival, order);
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private MockMultipartFile validImage() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "wide.png", "image/png", bytes.toByteArray());
    }

    @Test void listAndUploadAreFestivalScopedAndAppend() throws Exception {
        when(settings.existsById(1L)).thenReturn(true);
        when(settings.existsById(2L)).thenReturn(true);
        var first = image(10, 1, 0);
        when(images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first));
        when(images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(2L)).thenReturn(List.of());
        when(images.save(any(HomeImage.class))).thenAnswer(call -> {
            HomeImage saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);
            return saved;
        });
        var file = validImage();
        when(storage.uploadHomeImage(1L, file)).thenReturn("https://cdn/new.png");

        assertEquals(List.of(10L), service.list(1L).stream().map(dto -> dto.id()).toList());
        assertTrue(service.list(2L).isEmpty());
        var added = service.upload(1L, file);
        assertEquals(11L, added.id());
        assertEquals(1, added.displayOrder());
        assertEquals("https://cdn/new.png", added.imageUrl());
        verify(storage).uploadHomeImage(1L, file);
        verify(storage, never()).uploadHomeImage(eq(2L), any());
    }

    @Test void rejectsBadImageBeforeStorage() throws Exception {
        when(settings.existsById(1L)).thenReturn(true);
        var tooLarge = new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        var fake = new MockMultipartFile("file", "fake.png", "image/png", "fake".getBytes());
        assertThrows(ResponseStatusException.class, () -> service.upload(1L, tooLarge));
        assertThrows(ResponseStatusException.class, () -> service.upload(1L, fake));
        assertThrows(ResponseStatusException.class, () -> service.upload(99L, validImage()));
        verifyNoInteractions(storage);
    }

    @Test void deleteCannotCrossFestival() {
        when(settings.existsById(1L)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.delete(1L, 20L));
        verify(images, never()).delete(any());
        var own = image(10, 1, 0);
        when(images.findByIdAndFestivalSettingId(10L, 1L)).thenReturn(Optional.of(own));
        service.delete(1L, 10L);
        verify(images).delete(own);
    }

    @Test void reorderPersistsExactFestivalListAndRejectsForeignOrDuplicateIds() {
        when(settings.existsById(1L)).thenReturn(true);
        var first = image(10, 1, 0);
        var second = image(11, 1, 1);
        when(images.findAllByFestivalSettingIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first, second));
        assertThrows(ResponseStatusException.class, () -> service.reorder(1L, List.of(10L, 99L)));
        assertThrows(ResponseStatusException.class, () -> service.reorder(1L, List.of(10L, 10L)));
        assertThrows(ResponseStatusException.class, () -> service.reorder(1L, List.of(10L)));
        var result = service.reorder(1L, List.of(11L, 10L));
        assertEquals(List.of(11L, 10L), result.stream().map(dto -> dto.id()).toList());
        assertEquals(0, second.getDisplayOrder());
        assertEquals(1, first.getDisplayOrder());
    }
}

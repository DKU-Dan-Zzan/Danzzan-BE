package com.danzzan.domain.festival.service;

import com.danzzan.infra.s3.S3Uploader;
import com.danzzan.infra.s3.S3UploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.io.IOException;

/** Upload is staged; the ticketing settings save publishes the returned URL. */
@Service
@RequiredArgsConstructor
public class TicketingBackgroundService {
    private final S3Uploader uploader;
    public S3UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024L)
            throw invalidImage();
        String type = file.getContentType();
        if (!"image/jpeg".equals(type) && !"image/png".equals(type)) throw invalidImage();
        try (var input = file.getInputStream(); var imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) throw invalidImage();
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw invalidImage();
            var reader = readers.next();
            try {
                reader.setInput(imageInput);
                String format = reader.getFormatName();
                if (!("image/png".equals(type) && "png".equalsIgnoreCase(format))
                    && !("image/jpeg".equals(type) && "jpeg".equalsIgnoreCase(format))) throw invalidImage();
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > 40_000_000L || reader.read(0) == null) throw invalidImage();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) { throw invalidImage(); }
        return uploader.uploadTicketingBackground(file);
    }
    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "10MB 이하, 4천만 화소 이하의 JPG·PNG 이미지를 선택해 주세요.");
    }
}

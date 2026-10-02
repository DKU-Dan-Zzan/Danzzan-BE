package com.danzzan.domain.festival;
import com.danzzan.domain.festival.service.TicketingBackgroundService;
import com.danzzan.infra.s3.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TicketingBackgroundServiceTest {
    private final S3Uploader uploader = mock(S3Uploader.class);
    private final TicketingBackgroundService service = new TicketingBackgroundService(uploader);
    @Test void uploadsVerifiedImage() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file","poster.png","image/png",bytes.toByteArray());
        when(uploader.uploadTicketingBackground(file)).thenReturn(new S3UploadResult("key", "https://example.com/poster.png"));
        assertEquals("https://example.com/poster.png",service.upload(file).url());
    }
    @Test void rejectsFakeImageAndSvgBeforeStorage() {
        for (String type : new String[]{"image/png", "image/svg+xml", "text/html"}) {
            assertThrows(ResponseStatusException.class, () -> service.upload(new MockMultipartFile("file","fake.png",type,"<script>alert(1)</script>".getBytes())));
        }
        verifyNoInteractions(uploader);
    }
    @Test void rejectsEmptyAndOversizeImage() {
        assertThrows(ResponseStatusException.class, () -> service.upload(new MockMultipartFile("file",new byte[0])));
        assertThrows(ResponseStatusException.class, () -> service.upload(new MockMultipartFile("file","big.png","image/png",new byte[10*1024*1024+1])));
        verifyNoInteractions(uploader);
    }
}

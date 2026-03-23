package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.response.AdvertisementImagePresignResponse;
import com.danzzan.infra.s3.S3PresignedPutResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAdvertisementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminAdvertisementService adminAdvertisementService;

    @BeforeEach
    void setUp() {
        AdminAdvertisementController controller = new AdminAdvertisementController(adminAdvertisementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void 광고_이미지_presign_엔드포인트를_제공한다() throws Exception {
        when(adminAdvertisementService.presignAdImage(any()))
                .thenReturn(mockPresignResponse());

        mockMvc.perform(
                        post("/api/admin/ads/images/presign")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fileName": "banner.png",
                                          "contentType": "image/png",
                                          "fileSize": 1024
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrl").value("https://s3.example.com/presigned"))
                .andExpect(jsonPath("$.fileUrl").value("https://cdn.example.com/ads/banner.png"))
                .andExpect(jsonPath("$.method").value("PUT"));
    }

    @Test
    void 광고_이미지_uploadUrl_레거시_엔드포인트를_제공한다() throws Exception {
        when(adminAdvertisementService.presignAdImage(any()))
                .thenReturn(mockPresignResponse());

        mockMvc.perform(
                        post("/api/admin/ads/upload-url")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fileName": "banner.png",
                                          "contentType": "image/png",
                                          "fileSize": 1024
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presignedUrl").value("https://s3.example.com/presigned"))
                .andExpect(jsonPath("$.fileUrl").value("https://cdn.example.com/ads/banner.png"))
                .andExpect(jsonPath("$.method").value("PUT"));
    }

    private AdvertisementImagePresignResponse mockPresignResponse() {
        return AdvertisementImagePresignResponse.from(
                new S3PresignedPutResult(
                        "ads/2026/03/banner.png",
                        "https://cdn.example.com/ads/banner.png",
                        "https://s3.example.com/presigned",
                        Instant.parse("2026-03-23T00:00:00Z")
                )
        );
    }
}

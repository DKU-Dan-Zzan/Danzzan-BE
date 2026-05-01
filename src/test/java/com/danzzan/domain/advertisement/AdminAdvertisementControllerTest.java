package com.danzzan.domain.advertisement;

import com.danzzan.domain.advertisement.dto.AdvertisementResponse;
import com.danzzan.domain.advertisement.dto.CreateAdvertisementRequest;
import com.danzzan.domain.advertisement.dto.response.AdvertisementImagePresignResponse;
import com.danzzan.infra.s3.S3PresignedPutResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Test
    void 광고_ID_수정_엔드포인트를_제공한다() throws Exception {
        when(adminAdvertisementService.updateById(eq(7L), any()))
                .thenReturn(mockAdvertisementResponse("https://example.com/landing"));

        mockMvc.perform(
                        patch("/api/admin/ads/item/7")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "수정 배너",
                                          "imageUrl": "https://cdn.example.com/banner-new.png",
                                          "linkUrl": "https://example.com/landing",
                                          "placement": "HOME_BOTTOM"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("수정 배너"))
                .andExpect(jsonPath("$.linkUrl").value("https://example.com/landing"))
                .andExpect(jsonPath("$.placement").value("HOME_BOTTOM"));
    }

    @Test
    void 광고_수정_요청에서_link_url_별칭은_바인딩하지_않는다() throws Exception {
        when(adminAdvertisementService.updateById(eq(8L), any()))
                .thenReturn(mockAdvertisementResponse(null));

        mockMvc.perform(
                        patch("/api/admin/ads/item/8")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "수정 배너",
                                          "imageUrl": "https://cdn.example.com/banner-new.png",
                                          "link_url": "https://legacy.example.com",
                                          "placement": "HOME_BOTTOM"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkUrl").value(nullValue()));

        ArgumentCaptor<CreateAdvertisementRequest> captor =
                ArgumentCaptor.forClass(CreateAdvertisementRequest.class);
        verify(adminAdvertisementService).updateById(eq(8L), captor.capture());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getLinkUrl());
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

    private AdvertisementResponse mockAdvertisementResponse(String linkUrl) {
        Advertisement ad = new Advertisement();
        ad.setId(7L);
        ad.setTitle("수정 배너");
        ad.setImageUrl("https://cdn.example.com/banner-new.png");
        ad.setLinkUrl(linkUrl);
        ad.setPlacement(AdvertisementPlacement.HOME_BOTTOM);
        ad.setIsActive(true);
        ad.setCreatedAt(LocalDateTime.of(2026, 4, 30, 9, 0));
        ad.setUpdatedAt(LocalDateTime.of(2026, 4, 30, 9, 5));
        return AdvertisementResponse.from(ad);
    }
}

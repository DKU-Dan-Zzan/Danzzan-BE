package com.danzzan.infra.image;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImageReadUrlHttpTest {
    @Test void renewalContractReturnsOnlyTheUrlAndDisablesResponseCaching() throws Exception {
        var service = mock(ImageReadUrlService.class);
        when(service.renew("https://example.blob.core.windows.net/images/photo.png?sig=old", null))
                .thenReturn("https://example.blob.core.windows.net/images/photo.png?sig=new");
        var mvc = MockMvcBuilders.standaloneSetup(new ImageReadUrlController(service)).build();
        mvc.perform(post("/images/read-url").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://example.blob.core.windows.net/images/photo.png?sig=old\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.blob.core.windows.net/images/photo.png?sig=new"));
        mvc.perform(post("/images/read-url").contentType(MediaType.APPLICATION_JSON).content("{\"imageUrl\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(service, times(1)).renew(anyString(), any());
    }
}

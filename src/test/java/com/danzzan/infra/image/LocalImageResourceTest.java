package com.danzzan.infra.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.URI;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalImageResourceTest {
    @TempDir Path directory;

    @Test
    void localProfileServesUploadedImageOverHttpPath() {
        new WebApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-storage"))
                .withUserConfiguration(MvcConfig.class, LocalImageStorage.class, LocalImageResourceConfig.class)
                .withPropertyValues("local-image.root-directory=" + directory,
                        "local-image.public-base-url=http://localhost:8080/uploads")
                .run(context -> {
                    MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", new byte[]{1, 2, 3});
                    String url = context.getBean(LocalImageStorage.class).uploadPoster(1L, file);
                    var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
                    mvc.perform(get(URI.create(url).getPath()))
                            .andExpect(status().isOk())
                            .andExpect(content().bytes(file.getBytes()));
                    mvc.perform(get("/uploads/festival/1/poster/%2e%2e/logo/secret.png"))
                            .andExpect(status().is4xxClientError());
                });
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfig {}
}

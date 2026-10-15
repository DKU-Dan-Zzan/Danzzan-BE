package com.danzzan.infra.image;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
@Profile("local-storage & !prod & !azure")
@RequiredArgsConstructor
public class LocalImageResourceConfig implements WebMvcConfigurer {
    private final LocalImageStorage storage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(storage.rootDirectory().toUri().toString())
                .resourceChain(true)
                .addResolver(new PathResourceResolver());
    }
}

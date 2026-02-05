package com.danzzan.ticketing.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DANSPOT Ticketing API")
                        .description("단국대학교 축제 티켓팅 서비스 API")
                        .version("v1.0.0"));
    }
}

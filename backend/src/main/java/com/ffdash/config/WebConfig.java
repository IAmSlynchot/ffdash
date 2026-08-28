package com.ffdash.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Vite dev server (a separate origin in development) to call the API.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LeaguesProperties properties;

    public WebConfig(LeaguesProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getCorsAllowedOrigin())
                .allowedMethods("GET");
    }
}

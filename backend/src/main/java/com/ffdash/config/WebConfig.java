package com.ffdash.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the frontend (a separate origin from the backend both in local dev,
 * via the Vite dev server, and in production, as a separately hosted static
 * site) to call the API.
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
                .allowedOrigins(properties.getCorsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET");
    }
}

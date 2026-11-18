package com.shuttleflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets a frontend served on its own origin call the API with the login cookie (the Next.js app normally
 * proxies /api to the API, so its requests are same-origin and need no CORS).
 * Credentials require an explicit origin list, never "*".
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${shuttleflow.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type")
                .allowCredentials(true);
    }
}

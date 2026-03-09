package com.milktea.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AgentConfig implements WebMvcConfigurer {

    @Value("${app.frontend.version:v1}")
    private String frontendVersion;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Route "/" to the appropriate index page based on frontend version config
        // v1: original MilkTea frontend (index.html) - Spring Boot default behavior
        // v2: financial report analysis frontend (index-finance.html)
        if ("v2".equalsIgnoreCase(frontendVersion)) {
            registry.addViewController("/").setViewName("forward:/index-finance.html");
        }
    }
}

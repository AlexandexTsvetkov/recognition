package ru.grafit.recognition.request.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@EnableR2dbcRepositories
public class R2dbcConfig {
    // Spring Boot автоматически создаст ConnectionFactory на основе свойств
}


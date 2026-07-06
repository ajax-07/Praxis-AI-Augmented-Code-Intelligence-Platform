package com.praxis.intake.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IntakeProperties.class)
public class IntakeConfig {
}

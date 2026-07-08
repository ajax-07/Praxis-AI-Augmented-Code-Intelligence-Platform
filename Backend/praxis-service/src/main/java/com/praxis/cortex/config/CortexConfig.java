package com.praxis.cortex.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CortexProperties.class)
public class CortexConfig {
}

package com.scheduler.custom.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesConfig {

    @Bean
    public ApiClient apiClient() throws Exception {
        ApiClient client = Config.defaultClient();
        return client;
    }
}

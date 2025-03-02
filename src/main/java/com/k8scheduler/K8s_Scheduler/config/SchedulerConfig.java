package com.k8scheduler.K8s_Scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;

@Configuration
public class SchedulerConfig {
    @SuppressWarnings("deprecation")
    @Bean
    public KubernetesClient kubernetesClient() {
        return new DefaultKubernetesClient();
    }
}

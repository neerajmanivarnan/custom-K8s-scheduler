package com.scheduler.custom.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Binding;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CustomSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomSchedulerService.class);
    private static final int DEFAULT_WATCH_TIMEOUT_SECONDS = 10;
    private static final long RETRY_DELAY_MS = 5000L;

    @Autowired
    private ApiClient apiClient;

    @Value("${scheduler.watch-timeout-seconds:" + DEFAULT_WATCH_TIMEOUT_SECONDS + "}")
    private int watchTimeoutSeconds;

    private CoreV1Api coreV1Api;
    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        logger.info("Initializing CustomSchedulerService with API server: {}", apiClient.getBasePath());
        coreV1Api = new CoreV1Api(apiClient);
        executorService = Executors.newSingleThreadExecutor();
        startWatchingPods();
    }

    private void startWatchingPods() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    watchPods();
                } catch (Exception e) {
                    logger.error("Failed to watch pods. API server: {}, Error: {}", 
                        apiClient.getBasePath(), e.getMessage(), e);
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warn("Pod watcher interrupted");
                        break;
                    }
                }
            }
        });
    }

    private void watchPods() throws ApiException, IOException {
        logger.debug("Starting pod watch with timeout: {}s", watchTimeoutSeconds);
        try (
            Watch<V1Pod> watch = Watch.createWatch(
                apiClient,
                coreV1Api.listPodForAllNamespacesCall(
                    null, null, null, null, null, null, null, null, null, watchTimeoutSeconds, true, null),
                    new TypeToken<Watch.Response<V1Pod>>() {}.getType()
                )
        ) {
            for (Watch.Response<V1Pod> event : watch) {
                processPodEvent(event);
            }
        } catch (ApiException e) {
            logger.error("API Exception watching pods: Code={}, Body={}", e.getCode(), e.getResponseBody(), e);
            throw e;
        }
    }

    private void processPodEvent(Watch.Response<V1Pod> event) {
        try {
            V1Pod pod = event.object;
            if (pod == null || pod.getSpec() == null || pod.getMetadata() == null) {
                logger.warn("Received invalid pod event: {}", event.type);
                return;
            }

            if (pod.getSpec().getNodeName() == null) {
                logger.info("Found unscheduled pod: {}", pod.getMetadata().getName());
                schedulePod(pod);
            }
        } catch (Exception e) {
            logger.error("Error processing pod event: {}", event.type, e);
        }
    }

    private void schedulePod(V1Pod pod) {
        try {
            List<V1Node> nodes = getAvailableNodes();
            findSuitableNode(pod, nodes)
                .ifPresentOrElse(
                    node -> bindPodToNodeSafe(pod, node),
                    () -> logger.warn("No suitable node found for pod: {}", pod.getMetadata().getName())
                );
        } catch (ApiException e) {
            logger.error("Error scheduling pod {}: Code={}, Body={}", 
                pod.getMetadata().getName(), e.getCode(), e.getResponseBody(), e);
        }
    }

    private List<V1Node> getAvailableNodes() throws ApiException {
        V1NodeList nodeList = coreV1Api.listNode(null, null, null, null, null, null, null, null, null, null, null);
        return nodeList.getItems();
    }

    private Optional<V1Node> findSuitableNode(V1Pod pod, List<V1Node> nodes) {
        return nodes.stream()
            .filter(node -> isNodeSuitable(node, pod))
            .findFirst();
    }

    private boolean isNodeSuitable(V1Node node, V1Pod pod) {
        if (node.getStatus() == null || node.getStatus().getConditions() == null) {
            return false;
        }

        boolean isReady = node.getStatus().getConditions().stream()
            .filter(c -> "Ready".equals(c.getType()))
            .anyMatch(c -> "True".equals(c.getStatus()));

        boolean noUnschedulableTaint = node.getSpec() == null || !Boolean.TRUE.equals(node.getSpec().getUnschedulable());
        
        return isReady && noUnschedulableTaint;
    }

    private void bindPodToNodeSafe(V1Pod pod, V1Node node) {
        try {
            bindPodToNode(pod, node);
            logger.info("Scheduled pod {} to node {}", pod.getMetadata().getName(), node.getMetadata().getName());
        } catch (ApiException e) {
            logger.error("Failed to bind pod {} to node {}: Code={}, Body={}", 
                pod.getMetadata().getName(), node.getMetadata().getName(), e.getCode(), e.getResponseBody(), e);
        }
    }

    private void bindPodToNode(V1Pod pod, V1Node node) throws ApiException {
        V1Binding binding = new V1Binding()
            .metadata(new V1ObjectMeta().name(pod.getMetadata().getName()))
            .target(new V1ObjectReference()
                .apiVersion("v1")
                .kind("Node")
                .name(node.getMetadata().getName()));

        coreV1Api.createNamespacedBinding(
            pod.getMetadata().getNamespace(),
            binding,
            null, null, null, null);
    }

    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}

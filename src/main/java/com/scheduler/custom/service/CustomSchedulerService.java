package com.scheduler.custom.service;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Watch;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.custom.Quantity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class CustomSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomSchedulerService.class);
    private static final int DEFAULT_WATCH_TIMEOUT_SECONDS = 10;
    private static final long RETRY_DELAY_MS = 5000L;
    private static final double ALPHA = 0.5;
    private static final double BETA = 0.3;
    private static final double GAMMA = 0.2;
    private static final double CPU_FACTOR = 1.0;
    private static final double MEMORY_FACTOR = 0.5;

    @Autowired
    private CoreV1Api coreV1Api;

    @Value("${scheduler.watch-timeout-seconds:" + DEFAULT_WATCH_TIMEOUT_SECONDS + "}")
    private int watchTimeoutSeconds;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        logger.info("Initializing CustomSchedulerService");
        executorService = Executors.newSingleThreadExecutor();
        startWatchingPods();
    }

    private void startWatchingPods() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    watchPods();
                } catch (Exception e) {
                    logger.error("Failed to watch pods: {}", e.getMessage(), e);
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

    private void watchPods() throws ApiException {
        logger.info("Starting pod watch with timeout: {}s", watchTimeoutSeconds);
        try (
            Watch<V1Pod> watch = Watch.createWatch(
                coreV1Api.getApiClient(),
                coreV1Api.listPodForAllNamespacesCall(
                    null, null, null, null, null, null, null, null, watchTimeoutSeconds, true, null),
                    new TypeToken<Watch.Response<V1Pod>>() {}.getType()
                )
        ) {
            for (Watch.Response<V1Pod> event : watch) {
                logger.debug("Received pod event: type={}, pod={}", event.type, event.object != null ? event.object.getMetadata().getName() : "null");
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
                logger.warn("Received invalid pod event: type={}, object={}", event.type, event.object);
                return;
            }

            String schedulerName = pod.getSpec().getSchedulerName();
            if (!"custom-scheduler".equals(schedulerName)) {
                logger.debug("Skipping pod {}: assigned to scheduler {}", pod.getMetadata().getName(), schedulerName);
                return;
            }

            if (pod.getSpec().getNodeName() == null) {
                logger.info("Found unscheduled pod: {}", pod.getMetadata().getName());
                schedulePod(pod);
            } else {
                logger.debug("Pod {} already scheduled to node {}", pod.getMetadata().getName(), pod.getSpec().getNodeName());
            }
        } catch (Exception e) {
            logger.error("Error processing pod event: type={}, error={}", event.type, e.getMessage(), e);
        }
    }

    private void schedulePod(V1Pod pod) {
        try {
            List<V1Node> nodes = getAvailableNodes();
            logger.debug("Found {} available nodes", nodes.size());

            List<V1Node> eligibleNodes = filterEligibleNodes(pod, nodes);
            logger.debug("Filtered to {} eligible nodes for pod {}", eligibleNodes.size(), pod.getMetadata().getName());

            if (eligibleNodes.isEmpty()) {
                logger.warn("No eligible nodes found for pod: {}", pod.getMetadata().getName());
                return;
            }

            V1Node bestNode = calculateBestNode(pod, eligibleNodes);
            if (bestNode != null) {
                bindPodToNodeSafe(pod, bestNode);
            } else {
                logger.warn("No suitable node found for pod: {}", pod.getMetadata().getName());
            }
        } catch (ApiException e) {
            logger.error("Error scheduling pod {}: Code={}, Body={}", 
                pod.getMetadata().getName(), e.getCode(), e.getResponseBody(), e);
        }
    }

    private List<V1Node> filterEligibleNodes(V1Pod pod, List<V1Node> nodes) {
        return nodes.stream()
            .filter(node -> {
                if (!checkTaintsAndTolerations(node, pod)) {
                    logger.debug("Node {} rejected: taints not tolerated", node.getMetadata().getName());
                    return false;
                }
                if (!checkAffinityRules(pod, node)) {
                    logger.debug("Node {} rejected: affinity rules not met", node.getMetadata().getName());
                    return false;
                }
                if (!checkAntiAffinityRules(pod, node)) {
                    logger.debug("Node {} rejected: anti-affinity rules violated", node.getMetadata().getName());
                    return false;
                }
                if (!hasSufficientResources(node, pod)) {
                    logger.debug("Node {} rejected: insufficient resources", node.getMetadata().getName());
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());
    }

    private boolean checkTaintsAndTolerations(V1Node node, V1Pod pod) {
        List<V1Taint> taints = node.getSpec().getTaints();
        List<V1Toleration> tolerations = pod.getSpec().getTolerations();
        if (taints == null || taints.isEmpty()) return true;
        if (tolerations == null) return false;

        for (V1Taint taint : taints) {
            boolean tolerated = tolerations.stream().anyMatch(t ->
                t.getKey().equals(taint.getKey()) &&
                (t.getOperator() == null || t.getOperator().equals("Equal") || t.getOperator().equals("Exists")) &&
                (t.getValue() == null || t.getValue().equals(taint.getValue())));
            if (!tolerated && "NoSchedule".equals(taint.getEffect())) return false;
        }
        return true;
    }

    private boolean checkAffinityRules(V1Pod pod, V1Node node) {
        V1Affinity affinity = pod.getSpec().getAffinity();
        if (affinity == null || affinity.getNodeAffinity() == null) return true;

        V1NodeAffinity nodeAffinity = affinity.getNodeAffinity();
        if (nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution() != null) {
            List<V1NodeSelectorTerm> terms = nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms();
            return terms.stream().anyMatch(term -> matchesNodeSelector(node, term));
        }
        return true;
    }

    private boolean matchesNodeSelector(V1Node node, V1NodeSelectorTerm term) {
        Map<String, String> labels = node.getMetadata().getLabels();
        if (labels == null) return false;
        return term.getMatchExpressions().stream().allMatch(expr -> {
            String key = expr.getKey();
            String value = expr.getValues() != null && !expr.getValues().isEmpty() ? expr.getValues().get(0) : null;
            switch (expr.getOperator()) {
                case "In": return labels.containsKey(key) && labels.get(key).equals(value);
                case "Exists": return labels.containsKey(key);
                default: return false;
            }
        });
    }

    private boolean checkAntiAffinityRules(V1Pod pod, V1Node node) {
        // Placeholder: Implement pod anti-affinity if needed
        return true;
    }

    private boolean hasSufficientResources(V1Node node, V1Pod pod) {
        V1ResourceRequirements req = pod.getSpec().getContainers().get(0).getResources();
        if (req == null || req.getRequests() == null) return true;

        long reqCpu = parseResource(req.getRequests().get("cpu"), "m");
        long reqMem = parseResource(req.getRequests().get("memory"), "Mi");
        long availCpu = getAvailableCpu(node);
        long availMem = getAvailableMemory(node);

        boolean sufficient = availCpu >= reqCpu && availMem >= reqMem;
        logger.debug("Node {} resources: CPU avail={}, req={}, Mem avail={}, req={}, sufficient={}", 
            node.getMetadata().getName(), availCpu, reqCpu, availMem, reqMem, sufficient);
        return sufficient;
    }

    private long parseResource(Quantity quantity, String unit) {
        if (quantity == null) return 0;
        String value = quantity.getNumber().toString();
        if (unit.equals("m")) {
            return quantity.getNumber().longValue(); // Assuming milliCPU
        } else if (unit.equals("Mi")) {
            return quantity.getNumber().longValue() / (1024 * 1024); // Convert bytes to MiB
        }
        return 0;
    }

    private long getAvailableCpu(V1Node node) {
        Map<String, Quantity> allocatable = node.getStatus().getAllocatable();
        return allocatable != null && allocatable.get("cpu") != null ? 
            parseResource(allocatable.get("cpu"), "m") : 0;
    }

    private long getAvailableMemory(V1Node node) {
        Map<String, Quantity> allocatable = node.getStatus().getAllocatable();
        return allocatable != null && allocatable.get("memory") != null ? 
            parseResource(allocatable.get("memory"), "Mi") : 0;
    }

    private V1Node calculateBestNode(V1Pod pod, List<V1Node> eligibleNodes) {
        String podName = pod.getMetadata().getName();
        double bestScore = Double.NEGATIVE_INFINITY;
        V1Node bestNode = null;

        double qosFactor = getQosFactor(pod);
        logger.debug("Pod {} QoS factor: {}", podName, qosFactor);

        for (V1Node node : eligibleNodes) {
            double resourceCostScore = calculateResourceCostScore(node, pod);
            double trafficCostScore = calculateTrafficCostScore(podName, node);
            double saturationScore = calculateSaturationScore(node);

            double nodeScore = qosFactor * (ALPHA * resourceCostScore - BETA * trafficCostScore - GAMMA * saturationScore);
            logger.debug("Node {} score: total={}, resource={}, traffic={}, saturation={}", 
                node.getMetadata().getName(), nodeScore, resourceCostScore, trafficCostScore, saturationScore);

            if (nodeScore > bestScore) {
                bestScore = nodeScore;
                bestNode = node;
            }
        }
        return bestNode;
    }

    private double getQosFactor(V1Pod pod) {
        V1ResourceRequirements req = pod.getSpec().getContainers().get(0).getResources();
        if (req == null || req.getRequests() == null) return 0.1; // BestEffort
        if (req.getLimits() != null && req.getRequests().equals(req.getLimits())) return 1.0; // Guaranteed
        return 0.5; // Burstable
    }

    private double calculateResourceCostScore(V1Node node, V1Pod pod) {
        long availCpu = getAvailableCpu(node);
        long availMem = getAvailableMemory(node);
        V1ResourceRequirements req = pod.getSpec().getContainers().get(0).getResources();
        long reqCpu = req != null && req.getRequests() != null ? parseResource(req.getRequests().get("cpu"), "m") : 0;
        long reqMem = req != null && req.getRequests() != null ? parseResource(req.getRequests().get("memory"), "Mi") : 0;

        double costAvailable = availCpu * CPU_FACTOR + availMem * MEMORY_FACTOR;
        double costRequested = reqCpu * CPU_FACTOR + reqMem * MEMORY_FACTOR;
        return costAvailable - costRequested;
    }

    private double calculateTrafficCostScore(String podName, V1Node node) {
        // Placeholder: Requires TrafficMatrix implementation
        return 0.0;
    }

    private double calculateSaturationScore(V1Node node) {
        Map<String, Quantity> capacity = node.getStatus().getCapacity();
        Map<String, Quantity> allocatable = node.getStatus().getAllocatable();
        long totalCpu = capacity != null && capacity.get("cpu") != null ? parseResource(capacity.get("cpu"), "m") : 0;
        long totalMem = capacity != null && capacity.get("memory") != null ? parseResource(capacity.get("memory"), "Mi") : 0;
        long availCpu = getAvailableCpu(node);
        long availMem = getAvailableMemory(node);
        long usedCpu = totalCpu - availCpu;
        long usedMem = totalMem - availMem;
        return totalCpu + totalMem == 0 ? 0 : (usedCpu + usedMem) / (double) (totalCpu + totalMem);
    }

    private List<V1Node> getAvailableNodes() throws ApiException {
        return coreV1Api.listNode(null, null, null, null, null, null, null, null, null, null).getItems();
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
        coreV1Api.createNamespacedBinding(pod.getMetadata().getNamespace(), binding, null, null, null);
    }

    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}

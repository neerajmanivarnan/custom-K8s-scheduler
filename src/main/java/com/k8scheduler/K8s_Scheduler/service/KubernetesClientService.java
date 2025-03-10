package com.k8scheduler.K8s_Scheduler.service;

import  com.k8scheduler.K8s_Scheduler.models.PodInfo;
import  com.k8scheduler.K8s_Scheduler.models.NodeInfo;
import  com.k8scheduler.K8s_Scheduler.utils.LoggerUtil;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class KubernetesClientService {
    private final KubernetesClient kubernetesClient;

    public KubernetesClientService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public List<PodInfo> getPendingPods() {

        List<PodInfo> pendingPods = new ArrayList<>();

        for (Pod pod : kubernetesClient.pods().inAnyNamespace().list().getItems()) {

            if (pod.getStatus().getPhase().equalsIgnoreCase("Pending")) {
                double cpu = Double.parseDouble(pod.getSpec().getContainers().get(0).getResources().getRequests().get("cpu").getAmount());
                double memory = Double.parseDouble(pod.getSpec().getContainers().get(0).getResources().getRequests().get("memory").getAmount());
                pendingPods.add(new PodInfo(pod.getMetadata().getName(), pod.getMetadata().getNamespace(), cpu, memory));
            }

        }

        LoggerUtil.logSuccess("Fetched " + pendingPods.size() + " pending pods.");
        return pendingPods;
    }

    public List<NodeInfo> getAvailableNodes() {

        List<NodeInfo> nodes = new ArrayList<>();

        for (Node node : kubernetesClient.nodes().list().getItems()) {
            double cpu = Double.parseDouble(node.getStatus().getCapacity().get("cpu").getAmount());
            double memory = Double.parseDouble(node.getStatus().getCapacity().get("memory").getAmount());
            nodes.add(new NodeInfo(node.getMetadata().getName(), cpu, memory));
        }

        LoggerUtil.logSuccess("Fetched " + nodes.size() + " available nodes.");
        return nodes;
    }

    public void bindPodToNode(String podName, String namespace, String nodeName) {

        kubernetesClient.pods().inNamespace(namespace).withName(podName)
                .edit(p -> new PodBuilder(p).editSpec().withNodeName(nodeName).endSpec().build());

        LoggerUtil.logSuccess("Pod " + podName + " bound to node " + nodeName);

    }
}

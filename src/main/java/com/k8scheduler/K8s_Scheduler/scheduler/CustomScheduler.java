package com.k8scheduler.K8s_Scheduler.scheduler;

import  com.k8scheduler.K8s_Scheduler.models.PodInfo;
import  com.k8scheduler.K8s_Scheduler.models.NodeInfo;
import  com.k8scheduler.K8s_Scheduler.service.KubernetesClientService;
import  com.k8scheduler.K8s_Scheduler.utils.LoggerUtil;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
// import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.WatcherException;

import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class CustomScheduler {
    private final KubernetesClientService k8sService;
    private final KubernetesClient kubernetesClient;

    public CustomScheduler(KubernetesClientService k8sService, KubernetesClient kubernetesClient) {
        this.k8sService = k8sService;
        this.kubernetesClient = kubernetesClient;
    }

    @PostConstruct
    public void startScheduler() {
        LoggerUtil.logSuccess("Custom Kubernetes Scheduler started... Watching for Pending Pods");

        // Watch for Pending pods and schedule them
        kubernetesClient.pods().inAnyNamespace().watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod pod) {
                if (action == Action.ADDED && pod.getStatus().getPhase().equalsIgnoreCase("Pending")) {
                    LoggerUtil.logSuccess("New pending pod detected: " + pod.getMetadata().getName());
                    schedulePod(pod);
                }
            }

            @Override
            public void onClose(WatcherException e) {
                LoggerUtil.logError("Watcher closed due to error: " + e.getMessage());
            }

            // @Override
            // public void onClose(WatcherException arg0) {
            //     // TODO Auto-generated method stub
            //     throw new UnsupportedOperationException("Unimplemented method 'onClose'");
            // }
        });
    }

    private void schedulePod(Pod pod) {
        List<NodeInfo> availableNodes = k8sService.getAvailableNodes();
        PodInfo podInfo = new PodInfo(
            pod.getMetadata().getName(),
            pod.getMetadata().getNamespace(),
            Double.parseDouble(pod.getSpec().getContainers().get(0).getResources().getRequests().get("cpu").getAmount()),
            Double.parseDouble(pod.getSpec().getContainers().get(0).getResources().getRequests().get("memory").getAmount())
        );

        NodeInfo bestNode = selectBestNode(podInfo, availableNodes);

        if (bestNode != null) {
            k8sService.bindPodToNode(podInfo.getName(), podInfo.getNamespace(), bestNode.getName());
            LoggerUtil.logSuccess("Pod " + podInfo.getName() + " successfully scheduled on node " + bestNode.getName());
        } else {
            LoggerUtil.logWarning("No suitable node found for pod: " + podInfo.getName());
        }
    }

    private NodeInfo selectBestNode(PodInfo pod, List<NodeInfo> nodes) {
        NodeInfo bestNode = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (NodeInfo node : nodes) {
            if (node.getCpuCapacity() >= pod.getCpuRequest() && node.getMemoryCapacity() >= pod.getMemoryRequest()) {
                double score = node.getCpuCapacity() - pod.getCpuRequest();
                if (score > bestScore) {
                    bestScore = score;
                    bestNode = node;
                }
            }
        }
        return bestNode;
    }
}

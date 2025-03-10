package com.k8scheduler.K8s_Scheduler.repo;

import com.k8scheduler.K8s_Scheduler.models.NodeInfo;
import com.k8scheduler.K8s_Scheduler.models.PodInfo;
// import io.fabric8.kubernetes.api.model.Pod;
// import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class KubernetesRepository {

    private final KubernetesClient client;

    public KubernetesRepository(KubernetesClient client) {
        this.client = client;
    }

    public List<PodInfo> getUnscheduledPods() {

        return client.pods().list().getItems().stream()
                .filter(pod -> pod.getSpec().getNodeName() == null)
                .map(pod -> {
                    PodInfo podInfo = new PodInfo();
                    podInfo.setName(pod.getMetadata().getName());
                    podInfo.setNamespace(pod.getMetadata().getNamespace());
                    podInfo.setQosClass(pod.getSpec().getPriorityClassName());
                    // Extract CPU & memory requests
                    return podInfo;
                })
                .collect(Collectors.toList());

    }

    public List<NodeInfo> getAvailableNodes() {

        return client.nodes().list().getItems().stream()
                .map(node -> {
                    NodeInfo nodeInfo = new NodeInfo();
                    nodeInfo.setName(node.getMetadata().getName());
                    // Extract available CPU & memory
                    return nodeInfo;
                })
                .collect(Collectors.toList());

    }

    public void assignPodToNode(String podName, String namespace, String nodeName) {

        client.pods().inNamespace(namespace).withName(podName)
                .edit(pod -> {
                    pod.getSpec().setNodeName(nodeName);
                    return pod;
                });

    }
}

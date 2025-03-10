// package com.k8scheduler.K8s_Scheduler;
//
// import static org.junit.jupiter.api.Assertions.assertNotNull;
// import static org.junit.jupiter.api.Assertions.assertTrue;
//
// import org.junit.jupiter.api.Test;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
//
// import io.fabric8.kubernetes.api.model.Node;
// import io.fabric8.kubernetes.api.model.NodeList;
// import io.fabric8.kubernetes.client.KubernetesClient;
// import io.fabric8.kubernetes.client.dsl.MixedOperation;
// import io.fabric8.kubernetes.client.dsl.Resource;
//
// @SpringBootTest
// public class SchedulerConfigTest {
//
//     private static final Logger logger = LoggerFactory.getLogger(SchedulerConfigTest.class);
//
//     @Autowired
//     private KubernetesClient kubernetesClient;
//
//     @Test
//     public void testClusterConnection() {
//         assertNotNull(kubernetesClient, "ERROR: KubernetesClient bean is null!");
//
//         // Log Cluster Details
//         logger.info("SUCCESS: Connected to Kubernetes Cluster");
//         logger.info("Cluster Master URL: {}", kubernetesClient.getMasterUrl());
//         logger.info("Namespace: {}", kubernetesClient.getNamespace());
//         logger.info("API Version: {}", kubernetesClient.getApiVersion());
//
//         // Fetch Node List
//         MixedOperation<Node, NodeList, Resource<Node>> nodes = (MixedOperation<Node, NodeList, Resource<Node>>) kubernetesClient.nodes();
//         assertNotNull(nodes, "ERROR: Unable to fetch nodes!");
//         int nodeCount = nodes.list().getItems().size();
//         logger.info("Number of Nodes in Cluster: {}", nodeCount);
//
//         // Fetch Pods (Default Namespace)
//         int podCount = kubernetesClient.pods().list().getItems().size();
//         logger.info("Number of Pods in Default Namespace: {}", podCount);
//
//         // Assert at least one node exists
//         assertTrue(nodeCount > 0, "ERROR: No nodes found in the cluster!");
//     }
// }

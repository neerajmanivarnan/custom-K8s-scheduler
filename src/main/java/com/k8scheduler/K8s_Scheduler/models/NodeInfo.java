package com.k8scheduler.K8s_Scheduler.models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NodeInfo {

    public NodeInfo() {
        //empty
    }

    private String name;
    private double cpuCapacity;
    private double memoryCapacity;
}

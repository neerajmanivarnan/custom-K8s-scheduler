package com.k8scheduler.K8s_Scheduler.models;

import lombok.Data;

@Data
public class PodInfo {

    public PodInfo(String name2, String namespace2, double double1, double double2) {
        //empty 
    }

    public PodInfo() {
        //empty 
    }

    private String name;
    private String namespace;
    private String qosClass;
    private int cpuRequest;
    private int memoryRequest;
}

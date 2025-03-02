package com.k8scheduler.K8s_Scheduler.models;

import lombok.Data;

@Data
public class PodInfo {
    public PodInfo(String name2, String namespace2, double double1, double double2) {
        //TODO Auto-generated constructor stub
    }
    public PodInfo() {
        //TODO Auto-generated constructor stub
    }
    private String name;
    private String namespace;
    private String qosClass;
    private int cpuRequest;
    private int memoryRequest;
}

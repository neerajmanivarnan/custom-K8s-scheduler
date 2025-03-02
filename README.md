# Custom Kubernetes Scheduler


<img src="https://github.com/kubernetes/kubernetes/raw/master/logo/logo.png" width="100">

A **custom scheduler** for Kubernetes that intelligently assigns Pods to Nodes. Completely built using **Java**.

---

## **Overview**
Kubernetes uses a **default scheduler** to assign Pods to Nodes based on resource availability. This project provides a **custom scheduler** that:
- Implements **Best-Fit** scheduling to optimize node utilization.
- Integrates seamlessly with Kubernetes.
- Logs scheduling decisions for debugging.

---

## **Architecture**
### 🔹 Custom Scheduler Flow:
1. **API Server** forwards scheduling requests to the custom scheduler.
2. The scheduler fetches **all available nodes**.
3. Runs a **Best-Fit algorithm** to find the most optimal node.
4. Calls the **Kubernetes API** to bind the Pod to the selected Node.

---

##  **Prerequisites**
- Kubernetes Cluster (Minikube or Cloud)
- Docker
- Helm (for deployment)
- Java 17 (Spring Boot backend)

---

## 🔧 **Setup & Deployment**

### **1️⃣ Build the Docker Image**
```sh
docker build -t neergasm/custom-scheduler:latest .

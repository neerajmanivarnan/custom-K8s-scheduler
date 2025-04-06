#!/bin/bash

# Exit on error
set -e

# Update Helm repositories
echo "Updating Helm repositories..."
helm repo update

# Install Prometheus in the monitoring namespace using kube-prometheus-stack chart
echo "Installing Prometheus in the monitoring namespace..."
helm install prometheus prometheus-community/kube-prometheus-stack --namespace monitoring

# Install Grafana in the monitoring namespace
echo "Installing Grafana in the monitoring namespace..."
helm install grafana grafana/grafana --namespace monitoring

# Wait for the services to be deployed
echo "Waiting for services to be deployed..."
kubectl rollout status deployment/prometheus-kube-prometheus-prometheus --namespace monitoring
kubectl rollout status deployment/grafana --namespace monitoring

# Port-forward Prometheus and Grafana
echo "Setting up port-forwarding for Prometheus..."
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090 &

echo "Setting up port-forwarding for Grafana..."
kubectl port-forward -n monitoring svc/grafana 3000:80 &

# Wait to ensure that the port-forwarding has been established
sleep 5

# Output the URLs for access
echo "Prometheus is available at http://localhost:9090"
echo "Grafana is available at http://localhost:3000"

echo "Installation complete. You can now access Prometheus and Grafana."

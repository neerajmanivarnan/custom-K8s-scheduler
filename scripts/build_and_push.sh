mvn clean package
docker build -t neergasm/custom-k8scheduler:0.1 .
docker push neergasm/custom-k8scheduler:0.1

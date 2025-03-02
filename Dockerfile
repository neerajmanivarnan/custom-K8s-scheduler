FROM maven:3-eclipse-temurin-17 as build
RUN mkdir /usr/src/project
COPY . /usr/src/project
WORKDIR /usr/src/project

RUN mvn clean package 

RUN jar xf target/K8s-Scheduler-0.0.1-SNAPSHOT.jar

RUN jdeps --ignore-missing-deps -q  \
    --recursive  \
    --multi-release 17  \
    --print-module-deps  \
    --class-path 'BOOT-INF/lib/*'  \
    target/K8s-Scheduler-0.0.1-SNAPSHOT.jar > deps.info
RUN jlink \
    --add-modules $(cat deps.info) \
    --strip-debug \
    --compress 2 \
    --no-header-files \
    --no-man-pages \
    --output /myjre
FROM debian:bookworm-slim
RUN groupadd -g 1001 scheduler && useradd -u 1001 -g scheduler -m -d /home/scheduler scheduler
ENV JAVA_HOME /user/java/jdk17
ENV PATH $JAVA_HOME/bin:$PATH
COPY --from=build /myjre $JAVA_HOME
RUN mkdir /project
COPY --from=build /usr/src/project/target/K8s-Scheduler-0.0.1-SNAPSHOT.jar /project/
USER 1001
WORKDIR /project
EXPOSE 8082
ENTRYPOINT java -jar K8s-Scheduler-0.0.1-SNAPSHOT.jar

# FROM eclipse-temurin:17-jdk-alpine
# WORKDIR /app
# COPY target/k8scheduler-0.0.1-SNAPSHOT.jar app.jar
# ENTRYPOINT ["java", "-jar", "app.jar"]


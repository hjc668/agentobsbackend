# 从仓库根目录构建：
# docker build --build-arg BASE_IMAGE=<行内 OpenJDK 8 基础镜像> -t agentobs-backend .
ARG BASE_IMAGE
FROM ${BASE_IMAGE}

USER root
WORKDIR /home/icbc

# 生产配置由部署平台挂载到 /home/icbc/config/application.yml，不打包进镜像。
RUN mkdir -p /home/icbc/config

COPY backend/target/langfuse-web-service-0.1.0-SNAPSHOT.jar /home/icbc/langfuse-web-service.jar
COPY scripts/start-backend-intranet.sh /home/icbc/start-backend.sh
RUN chmod 644 /home/icbc/langfuse-web-service.jar && chmod 755 /home/icbc/start-backend.sh

EXPOSE 8080

ENTRYPOINT ["/home/icbc/start-backend.sh"]

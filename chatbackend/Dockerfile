# Multi-stage build for Spring Boot application

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install runtime dependencies: wget + Python
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget python3 python3-venv python3-pip && \
    rm -rf /var/lib/apt/lists/*

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Copy mdfra model assets
COPY mdfra /app/mdfra
RUN rm -rf /app/mdfra/.venv

ARG CARA_PTH_URL
RUN set -e; \
    P="/app/mdfra/snapshots/best/MdfraNet-best.pth"; \
    echo "mdfra_weights_path=$P"; \
    SIZE=$(stat -c%s "$P" 2>/dev/null || echo 0); \
    if [ "$SIZE" -lt 1000000 ]; then \
      if [ -n "$CARA_PTH_URL" ]; then echo "download_weights_from=$CARA_PTH_URL"; wget -O "$P" "$CARA_PTH_URL"; fi; \
    fi; \
    SIZE=$(stat -c%s "$P" 2>/dev/null || echo 0); \
    echo "mdfra_weights_size=$SIZE"; \
    if [ "$SIZE" -lt 1000000 ]; then echo "mdfra_weights_pointer_suspect=true"; exit 1; fi

# Prepare Python venv and install required packages (CPU wheels)
RUN python3 -m venv /app/mdfra/.venv && \
    /app/mdfra/.venv/bin/pip install --no-cache-dir --upgrade pip && \
    /app/mdfra/.venv/bin/pip install --no-cache-dir pillow numpy safetensors && \
    /app/mdfra/.venv/bin/pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.5.1 torchvision==0.20.1

# Non-root user
RUN groupadd --system spring && useradd --system --gid spring --home /app spring && \
    chown -R spring:spring /app
USER spring

# Expose port
EXPOSE 8080

# Health check（后端在 application.yml 中设置了 context-path=/api）
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

# Python path for segmentation service
ENV PYTHON=/app/mdfra/.venv/bin/python

# Run application with environment variable support
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xms512m -Xmx1024m} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]


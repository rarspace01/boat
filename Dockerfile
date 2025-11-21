# Stage 1: Build custom JRE
FROM eclipse-temurin:25-jdk-noble AS jre-builder

RUN jlink --compress=zip-9 \
    --no-header-files \
    --strip-java-debug-attributes \
    --no-man-pages \
    --vm=server \
    --add-modules ALL-MODULE-PATH \
    --output /jre

# Stage 2: Final image
FROM debian:stable-slim

HEALTHCHECK --start-period=30s CMD curl --fail http://localhost:8080 || exit 1
VOLUME /tmp/boat

# Copy custom JRE from builder stage
COPY --from=jre-builder /jre /jre
ENV PATH="${PATH}:/jre/bin"

RUN apt-get update && apt-get install -y curl wget dnsutils \
    && curl https://rclone.org/install.sh -o install.sh \
    && chmod +x install.sh \
    && ./install.sh \
    && rm -f ./install.sh \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY boat.cfg boat.cfg
RUN mkdir -p /root/.config/rclone/
COPY rclone.conf /root/.config/rclone/rclone.conf

EXPOSE 8080
CMD wget https://github.com/rarspace01/boat/releases/latest/download/boat.jar -O boat.jar \
    && chmod +x boat.jar \
    && /jre/bin/java -XX:+HeapDumpOnOutOfMemoryError -jar boat.jar
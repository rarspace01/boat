# Example of custom Java runtime using jlink in a multi-stage container build
FROM eclipse-temurin:25 AS jre-build

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=zip-9 \
         --output /javaruntime

FROM debian:stable-slim
HEALTHCHECK --start-period=30s CMD curl --fail http://localhost:8080 || exit 1
VOLUME /tmp/boat
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME
SHELL ["/bin/bash", "-c"]
RUN apt-get update && apt-get install -y curl unzip dnsutils \
&& curl https://rclone.org/install.sh -o install.sh && chmod +x install.sh && ./install.sh && rm -f ./install.sh
#&& rm -rf /tmp/*
ENV PATH="${PATH}:./jre/bin"
#USER nobody
# copy config for boat & if used rlcone
COPY boat.cfg boat.cfg
RUN mkdir -p /root/.config/rclone/
COPY rclone.conf /root/.config/rclone/rclone.conf
EXPOSE 8080
CMD curl -L -o boat.jar https://github.com/rarspace01/boat/releases/latest/download/boat.jar && chmod +x boat.jar && java -XX:+HeapDumpOnOutOfMemoryError -jar boat.jar

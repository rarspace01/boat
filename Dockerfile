FROM eclipse-temurin:25-jre
HEALTHCHECK --start-period=30s CMD curl --fail http://localhost:8080 || exit 1
VOLUME /tmp/boat
SHELL ["/bin/bash", "-c"]
RUN apt-get update && apt-get install -y curl unzip dnsutils && apt-get clean \
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

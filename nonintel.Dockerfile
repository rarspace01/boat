FROM debian:stable-slim
HEALTHCHECK --start-period=30s CMD curl --fail http://localhost:8080 || exit 1
VOLUME /tmp/boat
ENV JAVA_HOME="./jdk"
SHELL ["/bin/bash", "-c"]
RUN apt-get update && apt-get install -y curl wget unzip zip dnsutils \
&& curl -s "https://get.sdkman.io" | bash && chmod +x "/root/.sdkman/bin/sdkman-init.sh" && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk install java 21-tem \ && jlink --compress=zip-9 --no-header-files --strip-java-debug-attributes --no-man-pages --vm=server --module-path "$HOME/.sdkman/candidates/java/current/jmods" --add-modules ALL-MODULE-PATH --output=./jre \
&& sdk uninstall --force java 21-tem && rm -rf ~/.sdkman  \
&& curl https://rclone.org/install.sh -o install.sh && chmod +x install.sh && ./install.sh && rm -f ./install.sh
#&& rm -rf /tmp/*
ENV PATH="${PATH}:./jre/bin"
#USER nobody
# copy config for boat & if used rlcone
COPY boat.cfg boat.cfg
RUN mkdir -p /root/.config/rclone/
COPY rclone.conf /root/.config/rclone/rclone.conf
EXPOSE 8080
CMD wget https://github.com/rarspace01/boat/releases/latest/download/boat.jar -O boat.jar && chmod +x boat.jar && ./jre/bin/java -XX:+HeapDumpOnOutOfMemoryError -jar boat.jar

FROM debian:stable-slim
HEALTHCHECK CMD curl -f http://localhost:8080/ || exit 1
VOLUME /app
ENV JAVA_HOME="./jdk"
RUN apt-get update && apt-get install -y curl wget unzip \
&& curl https://raw.githubusercontent.com/sormuras/bach/master/install-jdk.sh -o install-jdk.sh && chmod +x install-jdk.sh;./install-jdk.sh -f 16 --target ./jdk \
&& rm -rf jdk.tar.gz && rm -rf ./jdk/lib/src.zip \
&& ./jdk/bin/jlink --compress=2 --no-header-files --no-man-pages --vm=server --module-path ./jdk/jmods --add-modules ALL-MODULE-PATH --output=./jre \
&& rm -rf ./jdk/ \
&& curl https://rclone.org/install.sh -o install.sh && chmod +x install.sh && ./install.sh && rm -rf /tmp/*
ENV PATH="${PATH}:./jre/bin"
#USER nobody
# copy config for boat & if used rlcone
COPY boat.cfg boat.cfg
RUN mkdir -p /root/.config/rclone/
COPY rclone.conf /root/.config/rclone/rclone.conf
EXPOSE 8080
CMD wget https://github.com/rarspace01/boat/releases/latest/download/boat.jar -O boat.jar && chmod +x boat.jar && ./jre/bin/java -jar boat.jar

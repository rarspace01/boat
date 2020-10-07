FROM debian:stable-slim
HEALTHCHECK CMD curl -f http://localhost:8080/ || exit 1
ENV JAVA_HOME="./jdk"
RUN apt-get update && apt-get install -y curl wget unzip
RUN curl https://raw.githubusercontent.com/sormuras/bach/master/install-jdk.sh -o install-jdk.sh && chmod +x install-jdk.sh;./install-jdk.sh -f 15 --target ./jdk \
&& rm -rf jdk.tar.gz && rm -rf ./jdk/lib/src.zip \
&& ./jdk/bin/jlink --compress=2 --no-header-files --no-man-pages --vm=server --module-path ./jdk/jmods --add-modules ALL-MODULE-PATH --output=./jre \
&& rm -rf ./jdk/
ENV PATH="${PATH}:./jdk/bin"
ENV PATH="${PATH}:./jre/bin"
RUN curl https://rclone.org/install.sh -o install.sh && chmod +x install.sh && ./install.sh && rm -rf /tmp/*
#USER nobody
# copy config for pirateBoat & if used rlcone
COPY pirateboat.cfg pirateboat.cfg
COPY rclone.conf $HOME/.config/rclone/rclone.conf
RUN mkdir -p $HOME/.config/rclone/
RUN wget https://github.com/rarspace01/pirateboat/releases/latest/download/pirateboat.jar -O pirateboat.jar && chmod +x pirateboat.jar
EXPOSE 8080
CMD ./jre/bin/java -jar pirateboat.jar

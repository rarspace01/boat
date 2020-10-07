FROM debian:stable-slim
HEALTHCHECK CMD curl -f http://localhost:8080/ || exit 1
RUN apt-get update
RUN apt-get install -y wget curl unzip p7zip
RUN wget https://raw.githubusercontent.com/sormuras/bach/master/install-jdk.sh
RUN chmod +x install-jdk.sh;./install-jdk.sh -f 15 --target ./jdk
RUN wget https://rclone.org/install.sh
RUN chmod +x install.sh
RUN ./install.sh
#USER nobody
RUN wget https://raw.githubusercontent.com/rarspace01/pirateboat/master/runPirateboat.sh
RUN chmod +x runPirateboat.sh
ENV PATH="${PATH}:./jdk/bin"
# copy config for pirateBoat & if used rlcone
COPY pirateboat.cfg pirateboat.cfg
#install rclone config
RUN mkdir -p $HOME/.config/rclone/
COPY rclone.conf $HOME/.config/rclone/rclone.conf
#
EXPOSE 8080
CMD ./runPirateboat.sh

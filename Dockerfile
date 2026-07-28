FROM eclipse-temurin:25-jre
HEALTHCHECK --start-period=30s CMD curl --fail http://localhost:8080 || exit 1
VOLUME /tmp/boat
SHELL ["/bin/bash", "-c"]
RUN apt-get update && apt-get install -y curl unzip dnsutils && apt-get clean \
    && curl https://rclone.org/install.sh -o install.sh && chmod +x install.sh && ./install.sh && rm -f ./install.sh
RUN useradd -ms /bin/bash boatuser
USER boatuser
RUN touch /tmp/htpasswd
EXPOSE 8080 8081
CMD curl -L -o boat.jar https://github.com/rarspace01/boat/releases/latest/download/boat.jar && chmod +x boat.jar && rclone serve webdav /PFDB --addr :8081 --baseurl /PFDB --htpasswd /tmp/htpasswd & java -XX:+HeapDumpOnOutOfMemoryError -jar boat.jar

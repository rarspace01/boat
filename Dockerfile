# only ffprobe is used (duplicates), a static build is one file instead of the ~240 packages the ffmpeg package needs.
# It is a full build (dav1d for AV1, VVC, EVC, AVS2/3, ...) for amd64 and arm64.
FROM mwader/static-ffmpeg:9.0.2 AS ffmpeg

FROM eclipse-temurin:25-jre
COPY --from=ffmpeg /ffprobe /usr/local/bin/
HEALTHCHECK --start-period=30s CMD curl --fail http://localhost:8080 || exit 1
VOLUME /tmp/boat
SHELL ["/bin/bash", "-c"]
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl unzip dnsutils tmux \
    && rm -rf /var/lib/apt/lists/* \
    && curl https://rclone.org/install.sh -o install.sh && chmod +x install.sh && ./install.sh && rm -f ./install.sh
RUN useradd -ms /bin/bash boatuser
USER boatuser
WORKDIR /home/boatuser
RUN touch /tmp/htpasswd
EXPOSE 8080 8081
CMD tmux new -d 'rclone serve webdav /PFDB --addr :8081 --baseurl /PFDB --htpasswd /tmp/htpasswd' && curl -fL -o boat.jar https://github.com/rarspace01/boat/releases/latest/download/boat.jar && curl -fL -o boat.jar.sha256 https://github.com/rarspace01/boat/releases/latest/download/boat.jar.sha256 && sha256sum -c boat.jar.sha256 && chmod +x boat.jar && java -XX:+HeapDumpOnOutOfMemoryError -jar boat.jar

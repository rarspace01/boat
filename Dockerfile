# only ffprobe is used (duplicates), a static build is one file instead of the ~240 packages the ffmpeg package needs.
# It is a full build (dav1d for AV1, VVC, EVC, AVS2/3, ...) for amd64 and arm64.
FROM mwader/static-ffmpeg:9.0.2-1 AS ffmpeg
# rclone from its official image (a static binary for amd64 and arm64) instead of piping install.sh from the internet,
# so the version is pinned and dependabot updates it
FROM rclone/rclone:1.75.2 AS rclone

FROM eclipse-temurin:25-jre
COPY --from=ffmpeg /ffprobe /usr/local/bin/
COPY --from=rclone /usr/local/bin/rclone /usr/local/bin/
HEALTHCHECK --start-period=30s CMD curl --fail http://localhost:8080 || exit 1
VOLUME /tmp/boat
SHELL ["/bin/bash", "-c"]
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl dnsutils tmux \
    && rm -rf /var/lib/apt/lists/*
RUN useradd -ms /bin/bash boatuser
USER boatuser
WORKDIR /home/boatuser
RUN mkdir -p /home/boatuser/tmp && touch /home/boatuser/tmp/htpasswd
# both are plain http (boat logs in with Basic auth, webdav with the users of ~/tmp/htpasswd): publish them behind https
EXPOSE 8080 8081
CMD tmux new -d 'rclone serve webdav /PFDB --read-only --addr :8081 --baseurl /PFDB --htpasswd /home/boatuser/tmp/htpasswd' && curl -fL -o boat.jar https://github.com/rarspace01/boat/releases/latest/download/boat.jar && curl -fL -o boat.jar.sha256 https://github.com/rarspace01/boat/releases/latest/download/boat.jar.sha256 && sha256sum -c boat.jar.sha256 && chmod +x boat.jar && java -jar boat.jar

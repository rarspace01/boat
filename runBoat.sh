#!/bin/bash
# Downloads the latest release and starts it. The jar is only replaced if it matches the checksum of the release,
# otherwise the jar that is already there keeps running.
BASE_URL=https://github.com/rarspace01/boat/releases/latest/download
while :; do
    if wget -q -O boat.jar.new "$BASE_URL/boat.jar" && wget -q -O boat.jar.sha256 "$BASE_URL/boat.jar.sha256" \
        && echo "$(cut -d' ' -f1 boat.jar.sha256)  boat.jar.new" | sha256sum -c --quiet -; then
        mv boat.jar.new boat.jar
    else
        echo "download or checksum verification of boat.jar failed"
        rm -f boat.jar.new
    fi
    if [ -f boat.jar ]; then
        chmod +x boat.jar
        java -jar boat.jar
    else
        sleep 60
    fi
done

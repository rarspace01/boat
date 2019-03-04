#!/bin/bash
while :; do
    wget -O pirateboat.jar https://github.com/rarspace01/pirateboat/releases/latest/download/pirateboat.jar
    chmod +x pirateboat.jar
    java -jar pirateboat.jar
done

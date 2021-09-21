#!/bin/bash
while :; do
    wget -O boat.jar https://github.com/rarspace01/boat/releases/latest/download/boat.jar
    chmod +x boat.jar
    java -jar boat.jar
done

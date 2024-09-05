#!/bin/bash
docker build . -t rarspace01/tunnel -f DockerfileTunnel && docker run --restart always -d -it -p 8021:8021 rarspace01/tunnel

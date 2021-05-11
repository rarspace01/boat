#!/bin/bash
docker build . -t rarspace01/boat && docker run --restart always -d -it -p 8080:8080 rarspace01/boat

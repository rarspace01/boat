#!/bin/bash
# the Dockerfile builds on intel and arm cpus, its base images exist for both
docker build . -t rarspace01/boat && docker run --restart always -d -it -p 8080:8080 rarspace01/boat

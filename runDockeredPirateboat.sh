#!/bin/bash
docker build . -t rarspace01/pirateboat && docker run --restart always -d -it -p 8080:8080 rarspace01/pirateboat

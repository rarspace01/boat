#!/bin/bash
docker build . -t rarspace01/pirateboat && docker run -d -p 8080:8080 rarspace01/pirateboat

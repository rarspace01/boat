#!/bin/bash
#if [ `uname -m` -eq $zero ]; then
case $(uname -m) in
"x86_64" | "i386" | "i686")
  echo "Intel like CPU, use normal Dockerfile"
  docker build . -t rarspace01/boat && docker run --restart always -d -it -p 8080:8080 rarspace01/boat
  ;;
  *)
  echo "Non intel cpu - use dynamic dockerfile"
  docker build -f nonintel.Dockerfile . -t rarspace01/boat && docker run --restart always -d -it -p 8080:8080 rarspace01/boat
  ;;
esac

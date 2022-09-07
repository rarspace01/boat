# boat

the boat sails with wind

## Setup

Copy `boat.default.cfg` to `boat.cfg` and modify values accordingly. Copy `rclone.conf`
from `~/.config/rlcone/rclone.conf` for building the docker-image.
Run `/runBoat.sh` to have an autoupdate of the
newest version.

install certbot & generate certifiactes
```
sudo apt install certbot
sudo certbot certonly -d YOUR_DOMAIN
# renew with
certbot renew
sudo cp /etc/letsencrypt/live/YOUR_DOMAIN/privkey.pem /home/downloads/privkey.pem
sudo cp /etc/letsencrypt/live/YOUR_DOMAIN/fullchain.pem /home/downloads/fullchain.pem
sudo chmod 7777 /home/downloads/privkey.pem /home/downloads/fullchain.pem
```

tested with:

```
openjdk version "17 2021-09-14"
```

Build Status:

[![Java CI with Gradle](https://github.com/rarspace01/boat/actions/workflows/ci.yml/badge.svg)](https://github.com/rarspace01/boat/actions/workflows/ci.yml)
[![Coveralls](https://github.com/rarspace01/boat/actions/workflows/coveralls.yml/badge.svg)](https://github.com/rarspace01/boat/actions/workflows/coveralls.yml)
[![CodeQL](https://github.com/rarspace01/boat/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/rarspace01/boat/actions/workflows/codeql-analysis.yml)
[![Coverage Status](https://coveralls.io/repos/github/rarspace01/boat/badge.svg?branch=main)](https://coveralls.io/github/rarspace01/boat?branch=main)

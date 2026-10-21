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

## Users

All endpoints except `/` and `/boat/shutdown` need a login (HTTP Basic auth), the users are stored in the
`user` collection of the `boat` database in the MongoDB from `MONGO_URI`. Without a user nobody can log in, so the
first user has to be inserted by hand.

Create the password hash (bcrypt, prefixed with `{bcrypt}` so Spring Security knows the encoding):

```
echo "{bcrypt}$(htpasswd -bnBC 10 "" 'YOUR_PASSWORD' | tr -d ':\n')"
```

`htpasswd` is part of `apache2-utils` (`sudo apt install apache2-utils`). Any bcrypt generator works, the stored
value has to look like `{bcrypt}$2y$10$...` (`$2a$` and `$2b$` work as well).

Insert the user, e.g. with `mongosh "$MONGO_URI"`:

```
use boat
db.user.insertOne({
  "username": "YOUR_USERNAME",
  "password": "{bcrypt}$2y$10$REPLACE_WITH_THE_HASH_FROM_ABOVE",
  "roles": ["USER"]
})
```

or as a document for MongoDB Compass / Atlas:

```json
{
  "username": "YOUR_USERNAME",
  "password": "{bcrypt}$2y$10$REPLACE_WITH_THE_HASH_FROM_ABOVE",
  "roles": ["USER"]
}
```

Changing the password works the same way, update the `password` field with a new hash. A changed or removed user is
locked out immediately. Users with a `{bcrypt}` password are also written to `/tmp/htpasswd` every minute, for a
reverse proxy in front of boat.

tested with:

```
openjdk version "21.0.1 2023-10-17 LTS"
```


Build Status:

[![Java CI with Gradle](https://github.com/rarspace01/boat/actions/workflows/ci.yml/badge.svg)](https://github.com/rarspace01/boat/actions/workflows/ci.yml)
[![Coveralls](https://github.com/rarspace01/boat/actions/workflows/coveralls.yml/badge.svg)](https://github.com/rarspace01/boat/actions/workflows/coveralls.yml)
[![Coverage Status](https://coveralls.io/repos/github/rarspace01/boat/badge.svg?branch=main)](https://coveralls.io/github/rarspace01/boat?branch=main)

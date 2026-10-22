# boat

the boat sails with wind

## Requirements

| What | For |
|---|---|
| Java 25 (JRE to run, JDK 25 to build; the Docker image is `eclipse-temurin:25-jre`) | boat itself |
| MongoDB | users, transfers (`MONGO_URI`) |
| [rclone](https://rclone.org) with a configured remote | the library (`RCLONEDIR`), uploads and listings; has to be in the `PATH` |
| A multifilehoster account (Premiumize or Alldebrid) and its API key | downloads |
| TMDB and TVDB API keys | looking up titles, years and seasons |
| `wget` and `sha256sum` | `runBoat.sh` (updates and checks the jar) |
| [ffmpeg](https://ffmpeg.org) (`ffprobe`) in the `PATH` | only for finding duplicates in the library (`DuplicateCleaner`), it reads the resolution, codec and length of the videos. The Docker image has it. |
| `htpasswd` (`apache2-utils`) | optional, to create the password hash of the first user |
| `tmux` | optional, the Docker image serves the library with `rclone serve webdav` in it |
| certbot | optional, certificates for https |

## Setup

Copy `boat.default.cfg` to `boat.cfg` and modify values accordingly. Copy `rclone.conf`
from `~/.config/rlcone/rclone.conf` for building the docker-image.
Run `/runBoat.sh` to have an autoupdate of the
newest version.

Settings are read from `boat.cfg` (or `boat.default.cfg`) and can be overridden by environment variables:

| Setting | Default | |
|---|---|---|
| `MONGO_URI` | | **required**, boat doesn't start without it |
| `RCLONEDIR` | | **required**, the rclone path of the library, e.g. `remote:PFDB` |
| `PREMIUMIZE_APIKEY`, `ALLDEBRID_APIKEY` | | at least one multifilehoster, a warning is logged if none is set |
| `TFDB_APIKEY`, `TVDB_APIKEY` | | media lookup, a warning is logged if missing |
| `MODE` | `BOTH` | `SEARCH`, `DOWNLOAD` or `BOTH` |
| `MEDIA_CACHE_TTL_DAYS` | `90` | how long TMDB/TVDB results are cached, `0` keeps them forever (e.g. for local rename test runs) |
| `MEDIA_CACHE_NOT_FOUND_TTL_DAYS` | `7` | same for searches without a result |
| `SEARCH_MAX_RESULTS_PER_ENGINE` | `500` | how many results a search engine is asked for (paged where the engine has pages); the search itself shows the best 100 after filtering and sorting |
| `QUEUE_FILE` | `/tmp/boat/queue.list` | where the queue is stored |
| `HTPASSWD_FILE` | `/tmp/htpasswd` | where the users are written for a reverse proxy |
| `ALLOW_PRIVATE_URLS` | `false` | allow local addresses in search list urls |
| `RCLONE_NO_CHECK_CERTIFICATE` | `false` | pass `--no-check-certificate` to rclone |
| `DUPLICATE_CODEC_WEIGHT_PERCENT` | `100` | duplicates of the same resolution: how much the efficiency of the codec counts against the bitrate, `100` fully, `0` only the plain bitrate |

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

Changing the password works the same way, update the `password` field with a new hash. Logins are cached for 10
minutes, so a changed or removed user is locked out after that at the latest (restart boat to apply it right away). Users with a `{bcrypt}` password are also written to `/tmp/htpasswd` every minute, for a
reverse proxy in front of boat.

## TLS

Certificates are verified for all connections. Only torrent search sites with a broken certificate are retried
without verification, calls to the multifilehosters, TMDB, TVDB, GitHub and every url with an api key never are.
rclone verifies certificates as well, set `RCLONE_NO_CHECK_CERTIFICATE=true` in `boat.cfg` (or as environment
variable) to pass `--no-check-certificate`, e.g. for a webdav remote with a self-signed certificate.

## Search list urls

The search list accepts a url instead of titles. Only http(s) urls to public addresses are fetched (also after
redirects), at most 1 MB. Set `ALLOW_PRIVATE_URLS=true` in `boat.cfg` (or as environment variable) to allow
addresses in the local network.

## Duplicates in the library

`boat.rename.DuplicateCleaner` finds the same movie or episode more than once in the renamed library (same
`{tmdb-…}`/`{tvdb-…}` id, same season and episode) and deletes the worse versions. It needs `ffprobe` (see
requirements): the version with the higher resolution wins. At the same resolution the version with the higher quality
bitrate wins: its bitrate divided by what its codec needs for the same picture quality compared with H.264 (HEVC about
55%, AV1 45%, VP9 60%, xvid/DivX 190%, MPEG-2 230%, ...), so a HEVC file with 2.2 Mbit/s is worth an H.264 file with
4 Mbit/s. The values are rules of thumb (`DuplicateCleaner.CODEC_BITRATE_NEEDS`). `DUPLICATE_CODEC_WEIGHT_PERCENT` says how
much the codec counts: `100` fully (default), `0` not at all, only the plain bitrate (the most material for a later
re-encode). Versions which are not equally long (more than 5%) or can't be read are left alone and listed as
`UNSURE`. The subtitles of a deleted version move to the version which stays.

Start with a test run: the manual tests in `DuplicateCleanerIntegrationTest` (remove the `@Disabled`) write
`duplicates.log` and delete nothing. The first line of the log tells how fast `ffprobe` was (files per second, average,
median, 95th percentile and maximum per file). Look at the log, then run `deleteDuplicateMovies` or
`deleteDuplicateSeries`.

tested with:

```
openjdk version "25" 2025-09-16 LTS
```


Build Status:

[![Java CI with Gradle](https://github.com/rarspace01/boat/actions/workflows/ci.yml/badge.svg)](https://github.com/rarspace01/boat/actions/workflows/ci.yml)
[![Coveralls](https://github.com/rarspace01/boat/actions/workflows/coveralls.yml/badge.svg)](https://github.com/rarspace01/boat/actions/workflows/coveralls.yml)
[![Coverage Status](https://coveralls.io/repos/github/rarspace01/boat/badge.svg?branch=main)](https://coveralls.io/github/rarspace01/boat?branch=main)

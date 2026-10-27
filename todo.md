# TODO

What is left from the code review of `src/main/java/boat` and the decisions that were made on the way. The fixed findings
are in the git history (PRs #545 to #581), the settings are in the README.

## Open

Ordered by how much they matter, from the review of 2026-10-23 (on top of #581). The rest of that review is done.

### Bugs and risks

- [ ] Renamer: files logged as "unsure, title differs" are still moved. To decide: the same flag covers the wanted
  fuzzy matches (typos, "Azesu Hsoc Wpatu Avupu", roman numerals, see `ContentRenamerMovieTest`), not moving them would
  undo those; only the year search fallback (`MovieMatcher.searchMovieExact`, any title of the year) is really unsure.

### Features

- [ ] Alldebrid: up to date with the API (v4/v4.1, checked against docs.alldebrid.com and the live API with its demo
  key), but only Premiumize is registered in `MultifileHosterService`. Register it there to use it. It can do all
  Premiumize does except the cache check, AllDebrid has none anymore (a cached magnet is `ready` right after the upload).
- [ ] A scheduler that periodically checks the newest releases and whether they are already in the library: e.g.
  once a day the releases of the current and the last month from `BluRayComService.getReleasesForMonthAndYear`
  (kept for this, not used yet; a first try, `fillQueueWithMovies` in `DownloadMonitor`, is in the git history), each
  looked up with `MediaProxyService` and checked with `CloudService.findExistingFiles` (title and remote media id),
  the missing ones listed on the info page or added to the queue (`QueueService`). To decide: list only or queue,
  filters (year, genre, rating), and which source besides blu-ray.com (e.g. TMDB "now playing"/digital releases).
- [ ] Run the renamer and the duplicate cleaner from the app (endpoint or schedule, test run first), today only the
  manual integration tests do.
- [ ] AllDebrid tells right after the upload if a magnet was cached (`ready`), it could be shown on the info page.

## Decisions and known limits

- Tracker stats are optional: `TorrentInfoService` only asks the first tracker of the list. Hashes it doesn't know get
  no seeders from it, asking a second tracker isn't worth the longer search.
- The release jar is not signed: the checksum next to `boat.jar` protects against corrupted or tampered downloads, not
  against a compromised GitHub release. Accepted as is.
- `/boat/shutdown` and `/` stay unauthenticated (`permitAll`): the deployment/Docker setup restarts the app through
  `/boat/shutdown`. Don't secure it without changing the deployment first.
- All other endpoints need a login. Without users or without MongoDB nobody can log in, the first user is inserted by
  hand (README). A changed or removed user stays valid for up to 10 minutes (auth cache), users change 1-2 times a year.
- CSRF has no tokens: `CrossSiteRequestFilter` rejects requests the browser marks as `cross-site`/`same-site`
  (`Sec-Fetch-Site`), and `none` (typed urls, but also links handed over by native apps) for anything but GET/HEAD, so
  the endpoints changing state only take POST. Clients without the header (curl, scripts) still work.
- The Premiumize API key travels in query strings, its API requires it; it is redacted in logs. AllDebrid gets its key
  in the `Authorization` header.
- Transport: boat runs behind an nginx reverse proxy which terminates https, so boat itself serves plain http and
  nothing in the repository turns on TLS (no SSL bundle, no certbot setup beyond the README steps). The ports of the
  Docker image (8080 boat, 8081 `rclone serve webdav`) must not be published without that proxy. The webdav of the
  image stays writable for every htpasswd user (a `--read-only` was considered, not needed for this setup).
- TLS is verified. Only torrent search sites fall back to an unverified connection (never hosters, TMDB/TVDB, GitHub or
  urls with an api key, the host name is still checked). rclone verifies unless `RCLONE_NO_CHECK_CERTIFICATE=true`.
- Search list urls: only public addresses are fetched (`ALLOW_PRIVATE_URLS=true` to allow local ones). A DNS rebinding
  race between the check and the connection is possible, the endpoint needs a login.
- Downloads: a transfer whose upload to the drive failed, or whose hoster returned no files, is retried
  `MAX_UPLOAD_TO_DRIVE_RETRIES` (2) times, then it is `UPLOAD_TO_DRIVE_FAILED` and the files stay on the hoster
  (restart on the info page). The torrent is only deleted at the hoster after all files are uploaded. A transfer the hoster
  rejects (`SERVER_ERROR`) is sent again after 5, 10, 20, 40 and 80 minutes, then it is `ERROR` and the cleanup removes it.
- rclone `copyurl` skips a file which already exists with the size the hoster reports. The size doesn't prove identical
  content; a file of another size is replaced.
- Media proxy cache: `MediaProxyService.refreshCache` is only used by the manual `refreshLocalMediaProxyCache` test (refreshes
  the cache file of an installation), the entries are refreshed one by one when asked for after their ttl. Failed lookups are never cached; found entries live 90 days, not found 7 (`0` = forever).
- `ContentRenamer.processFilesFromDirectory` skips files which already have a `{tmdb-…}`/`{tvdb-…}` id. A file whose
  first pass name was imperfect is therefore not corrected by a later run, pass `skipFilesWithRemoteId = false` to check
  them again. Without a base path it defaults to `~/pfdb/PFDB`.
- Search: the engines return as many results as they have (up to `SEARCH_MAX_RESULTS_PER_ENGINE`, paged for Knaben, TorrentsCSV, YTS, Nyaa and LimeTorrents, stopping at torrents without seeders). Filtering (explicit content, bad quality, no size or magnet) is in `TorrentSearchEngineService` before the cache and tracker lookups; the list is cut to the best 100 (`MAX_SEARCH_RESULTS`) only after the final rating and sorting. `TorrentProject` and `TorrentDownloads` only fetch the magnet page of torrents with seeders (one request per torrent) and are not paged; `PirateBay` (100) and `TorrentDownload` (50) have no further pages.
- Search by title: the list of titles links every title to its page at TMDB or TVDB (to check it is the one) and, for a series, to S1..Sn (the season count comes from the details of the series, cached for a day; the searches don't have it). A season search only adds "S02" to the search string of the engines. What they find is not filtered by season afterwards (that could drop good torrents with other names like "Staffel 2" and the engines already match the term), the rating prefers torrents which match the term. A different series with the same title ("The Osugol UK" for "The Osugol") can show up, and EZTV drops season tags from its search.
- Duplicates in the library: `DuplicateCleaner` (`boat.rename`) finds the same movie (`{tmdb-…}` in the name, parts `- ptN` are different files) or episode (show id in the folder, `SxxEyy`, parts `S01E06b` and `S01E01-E02` are different) more than once and deletes the worse versions. The names of the library files often have no resolution or codec, so every file of a duplicate group is read with `ffprobe` (required, see README; `DUPLICATE_PROBE_THREADS`, default 8: on the rclone mount of the library it reads about 5 files/s, more threads don't help as the mount is the limit). A file which ffprobe rejects ("Invalid data found", "moov atom not found", no video stream) is broken and deleted if another version is readable; every file which can't be read is read once more after the others, and only a file rejected both times counts as broken. A file ffprobe couldn't read for another reason (a timeout, an I/O error of the mount, ffprobe missing) may be fine, its group is `UNSURE`. A file which can't be deleted keeps its subtitles, the log says `FAILED to delete`. Episodes with different titles in front of `SxxEyy` (Bogabo TAS / The Nuk Bogabo Apuziperot) are `UNSURE`. The better one: higher resolution class (the picture, not the name), then the bitrate divided by what the codec needs for the same quality compared with H.264 (`CODEC_BITRATE_NEEDS`: AV1 0.45, HEVC 0.55, VP9 0.6, H.264 1.0, xvid/DivX 1.9, MPEG-2 2.3, ...; rules of thumb), so 2.2 Mbit/s of HEVC are worth 4 Mbit/s of H.264. The better codec has a head start of 20% per generation (modern AV1/HEVC/VP9, H.264 class, older; `DUPLICATE_BETTER_CODEC_MARGIN_PERCENT`): it stays against an older generation with up to 20% more quality bitrate, above that the higher bitrate wins. `DUPLICATE_CODEC_WEIGHT_PERCENT` (default 100, `0` = only the bitrate) lets the plain bitrate count more, e.g. to keep the most material for a later re-encode. Groups which can't be decided are only listed as `UNSURE` and not touched: a version that ffprobe couldn't read (a timeout, an I/O error), a length or a codec that isn't known, or versions that differ more than 5% in length (a 25 minute against a 40 minute version, a cut file). A file which ffprobe rejects is a broken download and a version shorter than half of the longest one is an incomplete file, both are deleted when a complete version exists (a 25 minute episode against a double episode of 55 minutes goes too, the double episode is a renamer problem). If the versions differ in length, the official runtime of TMDB/TVDB decides (`OnlineRuntimeLookup`, only asked for these groups; one request per movie or season): versions from 15% shorter to 30% longer than it compete whatever their differences, shorter ones are incomplete and go, a longer one (several episodes in one file) leaves the group undecided; without a runtime or a version which matches it the 50% rule above applies. Parts of movies and double episodes have no runtime lookup. Not known: the source (remux/web) and the language of the audio, a German and an English version of an episode are duplicates to it, and the bitrates of different codecs are only comparable roughly (the table is a rule of thumb, encoders and material differ). A group with a codec that isn't in the table is `UNSURE`. Subtitles of a deleted version move to the version which stays (if it has none for that language), otherwise they go with it; versions with the same name and another extension share theirs. Always run it as a test run first (`DuplicateCleanerIntegrationTest`, manual, writes `duplicates.log` with the time ffprobe took).
- Tracker stats: one scrape request for up to 40 torrents, split in halves only if the tracker refuses the url as too long (413/414). The tracker list is
  cached for a day, three built in trackers are used while it can't be loaded.
- Tests: `./gradlew test` (and the CI build) only runs the offline tests. The ones that need the internet are tagged
  `@Tag("live")` and run with `./gradlew liveTest`, weekly and on request in `.github/workflows/live-tests.yml`. A broken
  search engine therefore only shows up there.
- Build scans were removed (Develocity plugin) for now.

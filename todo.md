# TODO

What is left from the code review of `src/main/java/boat` and the decisions that were made on the way. The fixed findings
are in the git history (PRs #545 to #569), the settings are in the README.

## Open

- [ ] `CloudFileService.getFilesInPath`: `waitFor(5s)` before reading the output always costs 5s on large listings (the
  process blocks on the full pipe until the timeout). Read the output while waiting.
- [ ] Ask a second tracker for the hashes the first one doesn't know (`TorrentInfoService` only asks the first tracker
  of the list, trackers know different subsets).
- [ ] Sign the release jar (or publish build attestations). The checksum next to `boat.jar` protects against corrupted
  or tampered downloads, not against a compromised GitHub release.
- [ ] `TorrentSearchEngineService` still creates a `ForkJoinPool` per search, sized by the number of active engines.
- [ ] Alldebrid: kept on purpose, but only Premiumize is registered in `MultifileHosterService`. Register it there to
  use it (the API key setting and the tests exist).
- [ ] `BluRayComService`: kept for finding BD releases to download, not used by anything yet.

## Decisions and known limits

- `/boat/shutdown` and `/` stay unauthenticated (`permitAll`): the deployment/Docker setup restarts the app through
  `/boat/shutdown`. Don't secure it without changing the deployment first.
- All other endpoints need a login. Without users or without MongoDB nobody can log in, the first user is inserted by
  hand (README). A changed or removed user stays valid for up to 10 minutes (auth cache), users change 1-2 times a year.
- CSRF has no tokens: `CrossSiteRequestFilter` rejects requests the browser marks as `cross-site`/`same-site`
  (`Sec-Fetch-Site`), clients without the header (curl, scripts) still work.
- API keys travel in query strings, the hoster APIs require it. They are redacted in logs.
- TLS is verified. Only torrent search sites fall back to an unverified connection (never hosters, TMDB/TVDB, GitHub or
  urls with an api key, the host name is still checked). rclone verifies unless `RCLONE_NO_CHECK_CERTIFICATE=true`.
- Search list urls: only public addresses are fetched (`ALLOW_PRIVATE_URLS=true` to allow local ones). A DNS rebinding
  race between the check and the connection is possible, the endpoint needs a login.
- Downloads: a transfer whose upload to the drive failed, or whose hoster returned no files, is retried
  `MAX_UPLOAD_TO_DRIVE_RETRIES` (2) times, then it is `UPLOAD_TO_DRIVE_FAILED` and the files stay on the hoster
  (restart on the debug page). The torrent is only deleted at the hoster after all files are uploaded.
- rclone `copyurl` skips a file which already exists with the size the hoster reports. The size doesn't prove identical
  content; a file of another size is replaced.
- Media proxy cache: failed lookups are never cached; found entries live 90 days, not found 7 (`0` = forever).
- `ContentRenamer.processFilesFromDirectory` skips files which already have a `{tmdb-…}`/`{tvdb-…}` id. A file whose
  first pass name was imperfect is therefore not corrected by a later run, pass `skipFilesWithRemoteId = false` to check
  them again. Without a base path it defaults to `~/pfdb/PFDB`.
- Search: the engines return as many results as they have (up to `SEARCH_MAX_RESULTS_PER_ENGINE`, paged for Knaben, TorrentsCSV, YTS, Nyaa and LimeTorrents, stopping at torrents without seeders). Filtering (explicit content, bad quality, no size or magnet) is in `TorrentSearchEngineService` before the cache and tracker lookups; the list is cut to the best 100 (`MAX_SEARCH_RESULTS`) only after the final rating and sorting. `TorrentProject` and `TorrentDownloads` only fetch the magnet page of torrents with seeders (one request per torrent) and are not paged; `PirateBay` (100) and `TorrentDownload` (50) have no further pages.
- Search by title: the list of titles links every title to its page at TMDB or TVDB (to check it is the one) and, for a series, to S1..Sn (the season count comes from the details of the series, cached for a day; the searches don't have it). A season search only adds "S02" to the search string of the engines. What they find is not filtered by season afterwards (that could drop good torrents with other names like "Staffel 2" and the engines already match the term), the rating prefers torrents which match the term. A different series with the same title ("The Office UK" for "The Office") can show up, and EZTV drops season tags from its search.
- Duplicates in the library: `DuplicateCleaner` (`boat.rename`) finds the same movie (`{tmdb-…}` in the name, parts `- ptN` are different files) or episode (show id in the folder, `SxxEyy`, parts `S01E06b` and `S01E01-E02` are different) more than once and deletes the worse versions. The names of the library files often have no resolution or codec, so every file of a duplicate group is read with `ffprobe` (required, see README; `DUPLICATE_PROBE_THREADS`, default 8: on the rclone mount of the library it reads about 5 files/s, more threads don't help as the mount is the limit). A file which can't be read is broken and deleted if another version is readable. Episodes with different titles in front of `SxxEyy` (Batman TAS / The New Batman Adventures) are `UNSURE`. The better one: higher resolution class (the picture, not the name), then the bitrate divided by what the codec needs for the same quality compared with H.264 (`CODEC_BITRATE_NEEDS`: AV1 0.45, HEVC 0.55, VP9 0.6, H.264 1.0, xvid/DivX 1.9, MPEG-2 2.3, ...; rules of thumb), so 2.2 Mbit/s of HEVC are worth 4 Mbit/s of H.264. The better codec has a head start of 20% per generation (modern AV1/HEVC/VP9, H.264 class, older; `DUPLICATE_BETTER_CODEC_MARGIN_PERCENT`): it stays against an older generation with up to 20% more quality bitrate, above that the higher bitrate wins. `DUPLICATE_CODEC_WEIGHT_PERCENT` (default 100, `0` = only the bitrate) lets the plain bitrate count more, e.g. to keep the most material for a later re-encode. Groups which can't be decided are only listed as `UNSURE` and not touched: a version that ffprobe can't read, a length or a codec that isn't known, or versions that differ more than 5% in length (a 25 minute against a 40 minute version, a cut file). A file which ffprobe can't read is a broken download and a version shorter than half of the longest one is an incomplete file, both are deleted when a complete version exists (a 25 minute episode against a double episode of 55 minutes goes too, the double episode is a renamer problem). If the versions differ in length, the official runtime of TMDB/TVDB decides (`OnlineRuntimeLookup`, only asked for these groups; one request per movie or season): versions from 15% shorter to 30% longer than it compete whatever their differences, shorter ones are incomplete and go, a longer one (several episodes in one file) leaves the group undecided; without a runtime or a version which matches it the 50% rule above applies. Parts of movies and double episodes have no runtime lookup. Not known: the source (remux/web) and the language of the audio, a German and an English version of an episode are duplicates to it, and the bitrates of different codecs are only comparable roughly (the table is a rule of thumb, encoders and material differ). A group with a codec that isn't in the table is `UNSURE`. Subtitles of a deleted version move to the version which stays (if it has none for that language), otherwise they go with it; versions with the same name and another extension share theirs. Always run it as a test run first (`DuplicateCleanerIntegrationTest`, manual, writes `duplicates.log` with the time ffprobe took).
- Tracker stats: one scrape request for up to 40 torrents, split in halves if the tracker refuses it. The tracker list is
  cached for a day, three built in trackers are used while it can't be loaded.
- Tests: `./gradlew test` (and the CI build) only runs the offline tests. The ones that need the internet are tagged
  `@Tag("live")` and run with `./gradlew liveTest`, weekly and on request in `.github/workflows/live-tests.yml`. A broken
  search engine therefore only shows up there.
- Build scans were removed (Develocity plugin) for now.

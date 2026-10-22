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
- Tracker stats: one scrape request for up to 40 torrents, split in halves if the tracker refuses it. The tracker list is
  cached for a day, three built in trackers are used while it can't be loaded.
- Tests: `./gradlew test` (and the CI build) only runs the offline tests. The ones that need the internet are tagged
  `@Tag("live")` and run with `./gradlew liveTest`, weekly and on request in `.github/workflows/live-tests.yml`. A broken
  search engine therefore only shows up there.
- Build scans were removed (Develocity plugin) for now.

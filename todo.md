# TODO

Findings from a code review of `src/main/java/boat`. Check items off as they get fixed.

## Notes / decisions

- `/boat/shutdown` stays unauthenticated (`permitAll`) for now: the deployment/Docker setup relies on it to restart the app. Don't secure it without changing the deployment first.

## Bugs

- [x] **Failed uploads still delete the torrent and transfer** (`MultifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst`): `delete(remoteTorrent)` and `transferService.delete(...)` run even when rclone failed. Fixed: only deleted after all files are uploaded, otherwise the transfer goes back to ready and is retried after the other waiting transfers.
- [x] A transfer whose upload to the drive keeps failing is retried forever. Fixed: at most `MAX_UPLOAD_TO_DRIVE_RETRIES` (2) retries, then `UPLOAD_TO_DRIVE_FAILED` and the files stay on the hoster.
- [x] A transfer whose hoster returns no files was retried every 30s forever. Fixed: counts like a failed upload, so it stops after `MAX_UPLOAD_TO_DRIVE_RETRIES` retries.
- [x] rclone `copyurl` retried 3 times on its own, each one downloading again. Fixed: `--retries 1`.
- [x] **Concurrent downloads / shared state races**: scheduled jobs are `@Async` with `fixedRate`, so runs overlap. `isDownloadInProgress` is a plain check-then-set `Boolean`; `localStatusStorage` is a `HashMap` and `activeTorrents` is mutated from several threads. Fixed in `MultifileHosterService` (`AtomicBoolean`, `ConcurrentHashMap`, `activeTorrents` replaced as a whole).
- [x] `TorrentMetaService` was unused; removed together with its test.
- [x] **Parallel search races** (`TorrentSearchEngineService`): `combineResults` and `tempActiveSearchEngines` are `ArrayList`s filled from `parallelStream()`; `activeSearchEngines` is cleared/refilled while being read; `ForkJoinPool(0)` throws when no engine is active.
- [x] **Empty `torrentId`** (`Torrent.remoteIdOrHash`): `requireNonNullElseGet` never falls back since `remoteId` is non-null, so URL transfers get `torrentId == ""` and `uri.contains("")` matches the first transfer (`updateUploadStatus`). `equals` is overridden without `hashCode`.
- [x] **`StringUtil.deductFirstTorrentLetter`**: `Ü` is mapped to `Ü` instead of `U` (ends up as `?`); titles starting with a year ("2001: A Space Odyssey") give an empty letter → `Movies//...`.
- [x] **`CloudService.isCloudTokenValid`** was a `val` evaluated once at startup. Fixed: evaluated on each access.
- [x] **`CloudFileService.getFilesInPath`** cached empty results for 12h; fixed with `unless = "#result.isEmpty()"`. Still open: `waitFor(5s)` before reading output always costs 5s on large listings.
- [x] **Premiumize**: `enrichCacheStateOfTorrents` sublist end `size - 1` skips the last torrent when >100 results; `catch (IOException)` never catches Jackson 3 (unchecked) exceptions; a null JSON field NPEs the whole transfer list. Fixed in #545 for the cache check and the transfer list.
- [x] **Premiumize** folder listing: any parse error or missing field now gives an empty file list instead of a partial one (which would upload some files and delete the rest).
- [x] **`updateIndexForLetters(joinToString())`**: joins with `", "` and splits per character, so `","` is treated as a folder letter.
- [x] **Version check**: no longer exits during an active upload, survives a GitHub rate-limit response, and matches `1.2.10` vs `1.2.1` on version boundaries.
- [x] **Name matching fallback** (`updateTransferStatus`) searched all transfers; it now skips transfers that were already matched.
- [x] **Transfer cleanup** deleted transfers waiting in `ADDED` after a day; they now get 7 days.
- [x] **`QueueService`**: streams are closed, the directory is created and errors are logged, entries are keyed by title and year, a missing year is no longer stored as `"null"`.
- [x] **`PropertiesHelper.getProperty(name, default)`** ignored `default` when the file or key was missing.

## Security

- [ ] (deliberately open, see notes) `/boat/shutdown` is `permitAll`.
- [x] No users in the database → every endpoint was open. Removed: all endpoints except `/` and `/boat/shutdown` always need a login; the README explains how to insert the first user.
- [x] XSS: torrent names (`Torrent.toHtml`), transfers (`Transfer.toString`), media titles, existing file names, `localSearchString` and the queued items were written into HTML unescaped. Fixed with `HtmlUtils.htmlEscape` / URL encoding.
- [x] CSRF disabled with HTTP Basic auth, and state-changing endpoints accept GET (`/boat/download`). Fixed with `CrossSiteRequestFilter`: requests the browser marks `Sec-Fetch-Site: cross-site`/`same-site` are rejected (clients without the header, e.g. curl, still work).
- [x] Shell injection: rclone commands were built via `bash -c` with interpolated URLs/paths. All rclone calls (`RcloneUploader`, `CloudFileService`, `ProcessUtil`) now pass an argument list.
- [x] TLS verification was disabled everywhere. Fixed: verified by default; only torrent search sites fall back to unverified TLS (never hosters, TMDB/TVDB, GitHub or urls with an api key). rclone verifies unless `RCLONE_NO_CHECK_CERTIFICATE=true`.
- [x] API keys in logs: `HttpHelper` no longer logs stack traces with the url and redacts `apikey`/`api_key`/`token`/`password` values; the Premiumize/Alldebrid log lines that printed urls are redacted too. Keys still travel in query strings (the hoster APIs require it).
- [x] `CachedAuthenticationProvider` kept the plaintext `user:password` as cache key. Fixed: keyed HMAC as key. Revoked or changed passwords stay valid for up to 10 minutes on purpose (users change 1-2 times a year).
- [x] SSRF: `searchList` fetched arbitrary urls server side. Fixed: `HttpHelper.getPublicPage` only fetches http(s) urls to public addresses, checks every redirect hop, reads at most 1 MB. `ALLOW_PRIVATE_URLS=true` allows local addresses. (A DNS rebinding race between check and connect remains, the endpoint needs a login.)
- [x] `runBoat.sh` / `Dockerfile` ran the latest release jar without verification. Fixed: the release workflow publishes `boat.jar.sha256`, both verify it before using the jar (`runBoat.sh` keeps the old jar on a mismatch). This protects against corrupted or tampered downloads, not against a compromised GitHub release; signing/attestation would be the next step.

## Improvements

- [ ] Split `ContentRenamer` (~2000 lines) and `MultifileHosterService`; remove duplicates (`getUploadDuration`/`getUploadStatusString`, the twice-copied name matching block, `remoteTorrents`/`remoteTorrentsForDownload`), the unused `getTorrentToBeDownloaded`, and commented-out code.
- [x] `getRemainingTrafficInMB()` is cached for 30 seconds. Still open: avoid `transferService.getAll()` once per uploaded file (`updateUploadStatus`).
- [ ] ~~`rcloneDownloadFileToDestination` creates two executors per file~~ (fixed by `RcloneUploader`); `TorrentInfoService` creates a pool with one thread per torrent per search. Use shared bounded executors.
- [ ] Replace `HttpHelper` with `java.net.http.HttpClient`: normal TLS, read error bodies, close streams (`downloadFileToPath` leaks), drop the ignored `params` argument.
- [x] `MediaProxyService`: failed lookups (no answer from TMDB/TVDB, as opposed to no results) are never cached, entries have a ttl (found 90 days, not found 7 days, `0` = forever; expired entries are looked up again on access and kept if that fails, entries untouched for twice the ttl are dropped when saving), the cache is capped at 20,000 entries (oldest dropped first), saved every 10 minutes and on shutdown. Entries of older cache files are still read, their empty entries are looked up again. `ContentRenamer.processFilesFromDirectory(skipFilesWithRemoteId = true)` skips files that already have a `{tmdb-…}`/`{tvdb-…}` id.
- [x] Fail fast on missing `MONGO_URI` and `RCLONEDIR` (`StartupConfigurationCheck`), warnings for missing API keys; `/tmp/boat/queue.list` and `/tmp/htpasswd` are configurable (`QUEUE_FILE`, `HTPASSWD_FILE`). The `~/pfdb/PFDB` fallback is gone from `MultifileHosterService` (`ContentRenamer` still defaults to it when called without a base path).
- [x] Removed the bean name dump and the redundant annotations in `Application`. Still open: use the logger instead of `println`/`printStackTrace` elsewhere.
- [x] `ContentRenamer.getTargetFile` uses `file.extension` (a name like `mkv.mkv` lost its extension).
- [x] `TorrentHelper.extractTorrentSizeFromString` supports TB/TiB; the two copies of it were merged.
- [ ] rclone `copyurl` overwrites existing library files; consider `--no-clobber`.
- [ ] Tests for the bugs above and HTML escaping; separate live search engine tests from unit tests.
- [x] `settings.gradle` accepted the build scan terms everywhere. Removed the Develocity plugin (and with it the build scans) for now.

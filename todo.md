# TODO

Findings from a code review of `src/main/java/boat`. Check items off as they get fixed.

## Notes / decisions

- `/boat/shutdown` stays unauthenticated (`permitAll`) for now: the deployment/Docker setup relies on it to restart the app. Don't secure it without changing the deployment first.

## Bugs

- [x] **Failed uploads still delete the torrent and transfer** (`MultifileHosterService.checkForDownloadableTorrentsAndDownloadTheFirst`): `delete(remoteTorrent)` and `transferService.delete(...)` run even when rclone failed. Fixed: only deleted after all files are uploaded, otherwise the transfer goes back to ready and is retried after the other waiting transfers.
- [ ] A transfer whose hoster returns no files, or whose upload keeps failing, is retried forever; consider a retry limit.
- [x] **Concurrent downloads / shared state races**: scheduled jobs are `@Async` with `fixedRate`, so runs overlap. `isDownloadInProgress` is a plain check-then-set `Boolean`; `localStatusStorage` is a `HashMap` and `activeTorrents` is mutated from several threads. Fixed in `MultifileHosterService` (`AtomicBoolean`, `ConcurrentHashMap`, `activeTorrents` replaced as a whole).
- [ ] `TorrentMetaService` still has its own copy of the unsynchronized `localStatusStorage`/`activeTorrents` code.
- [x] **Parallel search races** (`TorrentSearchEngineService`): `combineResults` and `tempActiveSearchEngines` are `ArrayList`s filled from `parallelStream()`; `activeSearchEngines` is cleared/refilled while being read; `ForkJoinPool(0)` throws when no engine is active.
- [x] **Empty `torrentId`** (`Torrent.remoteIdOrHash`): `requireNonNullElseGet` never falls back since `remoteId` is non-null, so URL transfers get `torrentId == ""` and `uri.contains("")` matches the first transfer (`updateUploadStatus`). `equals` is overridden without `hashCode`.
- [x] **`StringUtil.deductFirstTorrentLetter`**: `Ü` is mapped to `Ü` instead of `U` (ends up as `?`); titles starting with a year ("2001: A Space Odyssey") give an empty letter → `Movies//...`.
- [ ] **`CloudService.isCloudTokenValid`** is a `val` evaluated once at startup; an expired or late-ready token is never re-checked.
- [ ] **`CloudFileService.getFilesInPath`** caches empty results of failed rclone calls for 12h (`@Cacheable`), causing duplicate downloads; `waitFor(5s)` before reading output always costs 5s on large listings.
- [ ] **Premiumize**: `enrichCacheStateOfTorrents` sublist end `size - 1` skips the last torrent when >100 results; `catch (IOException)` never catches Jackson 3 (unchecked) exceptions; a null JSON field NPEs the whole transfer list.
- [x] **`updateIndexForLetters(joinToString())`**: joins with `", "` and splits per character, so `","` is treated as a folder letter.
- [ ] **Version check** (`DownloadMonitor.checkForUpdatedVersionAndShutdownIfUpdateAvailable`): exits during an active upload (transfer stuck in `UPLOADING_TO_DRIVE`); throws on a GitHub rate-limit response; `contains` treats `1.2.10` as `1.2.1`.
- [ ] **Name matching fallback** (`updateTransferStatus`) searches all transfers instead of unmatched ones, can overwrite an already matched transfer.
- [ ] **Transfer cleanup** deletes any transfer not updated for a day, including ones still waiting in `ADDED` for traffic.
- [ ] **`QueueService`**: streams not closed; fails silently when `/tmp/boat` doesn't exist; keyed by title only (same title, different year collide); missing year stored as `"null"`.
- [ ] **`PropertiesHelper.getProperty(name, default)`** ignores `default` when the file or key is missing.

## Security

- [ ] (deliberately open, see notes) `/boat/shutdown` is `permitAll`.
- [ ] No users in the database → every endpoint is open; `hasUsers` is only evaluated at startup.
- [ ] XSS: torrent names (`Torrent.toHtml`), media titles, existing file names and `localSearchString` are written into HTML unescaped.
- [ ] CSRF disabled with HTTP Basic auth, and state-changing endpoints accept GET (`/boat/download`).
- [ ] Shell injection: rclone commands built via `bash -c` with interpolated URLs/paths; use a `ProcessBuilder` argument list. Done for uploads (`RcloneUploader`), still open in `CloudFileService` (`lsjson`, `about`).
- [ ] TLS verification disabled everywhere (`HttpHelper` trust-all, rclone `--no-check-certificate`), including calls carrying API keys.
- [ ] API keys in query strings end up in logs (`Premiumize`, `Alldebrid`, `HttpHelper` stack traces).
- [ ] `CachedAuthenticationProvider` keys the cache with the plaintext `user:password`; revoked passwords stay valid for up to 10 minutes.
- [ ] SSRF: `searchList` fetches arbitrary URLs server side.
- [ ] `runBoat.sh` / `Dockerfile` run the latest release jar without checksum/signature verification.

## Improvements

- [ ] Split `ContentRenamer` (~2000 lines) and `MultifileHosterService`; remove duplicates (`getUploadDuration`/`getUploadStatusString`, the twice-copied name matching block, `remoteTorrents`/`remoteTorrentsForDownload`, `refreshTorrents`/`getTorrentsToDownload`, copy in `TorrentMetaService`), the unused `getTorrentToBeDownloaded`, the unused `authCache` bean and commented-out code.
- [ ] Cache `getRemainingTrafficInMB()` (2 HTTP calls, called very often); avoid `transferService.getAll()` once per uploaded file.
- [ ] ~~`rcloneDownloadFileToDestination` creates two executors per file~~ (fixed by `RcloneUploader`); `TorrentInfoService` creates a pool with one thread per torrent per search. Use shared bounded executors.
- [ ] Replace `HttpHelper` with `java.net.http.HttpClient`: normal TLS, read error bodies, close streams (`downloadFileToPath` leaks), drop the ignored `params` argument.
- [ ] `MediaProxyService`: empty results cached permanently, cache unbounded, `saveCache` only called from `ContentRenamer`, `refreshCache` never called.
- [ ] Fail fast on missing `MONGO_URI`, `RCLONE_DIR` and API keys (currently `MongoClients.create("")`, `"null/..."` paths); make `/tmp/boat`, `/tmp/htpasswd`, `~/pfdb/PFDB` configurable.
- [ ] Remove the bean name dump and redundant annotations in `Application`; use the logger instead of `println`/`printStackTrace`.
- [ ] `ContentRenamer.getTargetFile`: use `file.extension` instead of `name.replace(nameWithoutExtension, "")`.
- [ ] Support TB sizes in `TorrentHelper.extractTorrentSizeFromString`.
- [ ] rclone `copyurl` overwrites existing library files; consider `--no-clobber`.
- [ ] Tests for the bugs above and HTML escaping; separate live search engine tests from unit tests.
- [ ] `settings.gradle` agrees to the build scan terms, so every build publishes a public build scan; make it opt-in.

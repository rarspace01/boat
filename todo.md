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
- [ ] No users in the database → every endpoint is open; `hasUsers` is only evaluated at startup. Only a startup warning was added; making this fail closed needs a decision on how to bootstrap the first user.
- [x] XSS: torrent names (`Torrent.toHtml`), transfers (`Transfer.toString`), media titles, existing file names, `localSearchString` and the queued items were written into HTML unescaped. Fixed with `HtmlUtils.htmlEscape` / URL encoding.
- [x] CSRF disabled with HTTP Basic auth, and state-changing endpoints accept GET (`/boat/download`). Fixed with `CrossSiteRequestFilter`: requests the browser marks `Sec-Fetch-Site: cross-site`/`same-site` are rejected (clients without the header, e.g. curl, still work).
- [x] Shell injection: rclone commands were built via `bash -c` with interpolated URLs/paths. All rclone calls (`RcloneUploader`, `CloudFileService`, `ProcessUtil`) now pass an argument list.
- [ ] TLS verification disabled everywhere (`HttpHelper` trust-all, rclone `--no-check-certificate`), including calls carrying API keys.
- [ ] API keys in query strings end up in logs (`Premiumize`, `Alldebrid`, `HttpHelper` stack traces).
- [x] `CachedAuthenticationProvider` kept the plaintext `user:password` as cache key and accepted revoked passwords for 10 minutes. Fixed: keyed HMAC as key, hits are checked against the current stored password hash.
- [ ] SSRF: `searchList` fetches arbitrary URLs server side.
- [ ] `runBoat.sh` / `Dockerfile` run the latest release jar without checksum/signature verification.

## Improvements

- [ ] Split `ContentRenamer` (~2000 lines) and `MultifileHosterService`; remove duplicates (`getUploadDuration`/`getUploadStatusString`, the twice-copied name matching block, `remoteTorrents`/`remoteTorrentsForDownload`), the unused `getTorrentToBeDownloaded`, and commented-out code.
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

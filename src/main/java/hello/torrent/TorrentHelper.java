package hello.torrent;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TorrentHelper {
    private static TorrentService torrentService = new TorrentService();

    public static final double SIZE_UPPER_LIMIT = 15000.0;
    public static final double SEED_RATIO_UPPER_LIMIT = 5.0;
    public static final Comparator<Torrent> torrentSorter = (o1, o2) -> {
        if (o1.searchRating > o2.searchRating) {
            return -1;
        } else if (o1.searchRating < o2.searchRating) {
            return 1;
        } else {
            return Double.compare(o2.lsize, o1.lsize);
        }
    };
    public static final String TAG_REGEX = "(" + listOfReleaseTagsPiped() + ")";

    private static String listOfReleaseTagsPiped() {
        return String.join("|", torrentService.getReleaseTags());
    }

    public static double extractTorrentSizeFromString(Torrent tempTorrent) {
        long torrentSize = 0;
        if (tempTorrent.size.contains("GiB") || tempTorrent.size.contains("GB")) {
            torrentSize = (long) (Double.parseDouble(trimSizeStringToValue(tempTorrent)) * 1024);
        } else if (tempTorrent.size.contains("MiB") || tempTorrent.size.contains("MB")) {
            torrentSize = (long) (Double.parseDouble(trimSizeStringToValue(tempTorrent)));
        }
        return torrentSize;
    }

    private static String trimSizeStringToValue(Torrent tempTorrent) {
        return tempTorrent.size.replaceAll("(GiB)|(GB)|(MiB)|(MB)|(<.*?>)", "").trim();
    }

    public static Torrent evaluateRating(Torrent tempTorrent, String searchName) {
        tempTorrent.searchRating = 0;
        tempTorrent.debugRating = "";
        String torrentName = tempTorrent.name;
        if (torrentName == null || torrentName.trim().length() == 0) {
            return tempTorrent;
        }

        String normalizedTorrentName = getNormalizedTorrentString(torrentName);
        String normalizedSearchName = getNormalizedTorrentStringWithSpaces(searchName);

        if (normalizedTorrentName.contains(normalizedSearchName.trim().toLowerCase())) {
            tempTorrent.searchRating += 1;
            tempTorrent.debugRating += "🔍";
        }
        //check individual words
        List<String> searchWords = Arrays.asList(normalizedSearchName.trim().toLowerCase().split(" "));
        int searchMaxScore = searchWords.size();
        AtomicInteger matches = new AtomicInteger();
        searchWords.forEach(searchWord -> {
            if (normalizedTorrentName.contains(searchWord)) {
                matches.getAndIncrement();
            }
        });
        double matchScore = (double) matches.get() / (double) searchMaxScore;
        tempTorrent.searchRating += matchScore;
        tempTorrent.debugRating += String.format("🔍:%.2f", matchScore);

        // determine closeness
        if (normalizedTorrentName.length() > 0) {
            double closenessFactor = (double) normalizedSearchName.length() / (double) normalizedTorrentName.length();
            tempTorrent.searchRating += closenessFactor;
            tempTorrent.debugRating += String.format("🤲:%.2f", closenessFactor);
        }

        // calc first range
        double rangeRating = Math.min(tempTorrent.lsize, SIZE_UPPER_LIMIT) / SIZE_UPPER_LIMIT;
        tempTorrent.searchRating += rangeRating;
        tempTorrent.debugRating += String.format("📦:%.2f", rangeRating);
        // calculate seeder ratio
        double seedRatio = (double) tempTorrent.seeder / (double) tempTorrent.leecher;
        double seedRatioOptimized;
        if (tempTorrent.seeder >= 1 && tempTorrent.seeder <= 3) {
            seedRatioOptimized = 1.0;
        } else {
            seedRatioOptimized = seedRatio;
        }
        if (tempTorrent.isCached) {
            tempTorrent.debugRating += "🚄: 🚄🚄";
        } else if (seedRatio > 1.0) {
            double seedRating = Math.min(seedRatioOptimized, SEED_RATIO_UPPER_LIMIT) / SEED_RATIO_UPPER_LIMIT;
            tempTorrent.searchRating += seedRating;
            tempTorrent.debugRating += String.format("🚄:%.2f", seedRating);
        } else if (tempTorrent.seeder == 1) {
            tempTorrent.searchRating = tempTorrent.searchRating / 10;
            tempTorrent.debugRating += String.format("⚡🚄 OVR:%.2f", tempTorrent.searchRating / 10);
        }
        return tempTorrent;
    }

    public static String getNormalizedTorrentString(String name) {
        String lowerCase = name.replaceAll("(-[A-Z]+)", "").toLowerCase();
        return lowerCase.trim()
                .replaceAll(TAG_REGEX, "")
                .replaceAll("[()]+", "")
                .replaceAll("\\[[A-Za-z0-9. -]*\\]", "")
                .replaceAll("\\s", "")
                .replaceAll("\\.", "")
                ;
    }

    public static String getNormalizedTorrentStringWithSpaces(String name) {
        String lowerCase = name.replaceAll("(-[A-Z]+)", "").toLowerCase();
        return lowerCase.trim()
                .replaceAll(TAG_REGEX, "")
                .replaceAll("[()]+", "")
                .replaceAll("\\[[A-Za-z0-9. -]*\\]", "")
                .replaceAll("\\.", " ").trim();
    }

    public static String cleanNumberString(String value) {
        return value.replaceAll(",", "");
    }

    public static boolean isBlacklisted(Torrent torrent) {
        return torrent.name != null && (
                torrent.name.toLowerCase().contains("telesync") ||
                        torrent.name.toLowerCase().contains("telecine") ||
                        torrent.name.toLowerCase().contains(" hdcam") ||
                        torrent.name.toLowerCase().contains("tscam") ||
                        torrent.name.toLowerCase().contains(".cam.") ||
                        torrent.name.toLowerCase().contains("cam-rip") ||
                        torrent.name.toLowerCase().contains(".hdcam.") ||
                        torrent.name.toLowerCase().contains(" hdts") ||
                        torrent.name.toLowerCase().contains(" hd-ts") ||
                        torrent.name.toLowerCase().contains(".hd-ts") ||
                        torrent.name.toLowerCase().contains(".hdtc.") ||
                        torrent.name.toLowerCase().contains(".ts.") ||
                        torrent.name.toLowerCase().contains("[ts]") ||
                        torrent.name.toLowerCase().contains("pdvd") ||
                        torrent.name.toLowerCase().contains("predvdrip") ||
                        torrent.name.toLowerCase().contains("workprint") ||
                        torrent.name.toLowerCase().contains(".hdts.")
        );
    }

    public static boolean isValidTorrent(Torrent torrent) {
        return torrent.name != null &&
                torrent.magnetUri != null &&
                torrent.seeder > 0 &&
                !isBlacklisted(torrent);
    }

    public static boolean isValidMetaTorrent(Torrent torrent) {
        return torrent.name != null &&
                torrent.seeder > 0 &&
                !isBlacklisted(torrent);
    }

    public static String urlEncode(final String string) {
        try {
            return URLEncoder.encode(string, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return string;
        }
    }

    public static String buildMagnetUriFromHash(final String hash, final String torrentName) {
        return String.format("magnet:?xt=urn:btih:%s&dn=%s", hash, urlEncode(torrentName))
                + torrentService.getTrackerUrls().stream().map(TorrentHelper::urlEncode).collect(Collectors.joining("&tr=", "&tr=", ""));
    }

}

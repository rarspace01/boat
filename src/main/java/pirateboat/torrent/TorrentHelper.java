package pirateboat.torrent;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TorrentHelper {
    public static final String REGEX_RELEASE_GROUP = "(-[A-Za-z\\s]+)";
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
        return String.join("($|[ .-]+)|[ .]", torrentService.getReleaseTags());
    }

    public static double extractTorrentSizeFromString(Torrent tempTorrent) {
        long torrentSize = 0;
        try{
            if (tempTorrent.size.contains("GiB") || tempTorrent.size.contains("GB")) {
                torrentSize = (long) (Double.parseDouble(trimSizeStringToValue(tempTorrent)) * 1024);
            } else if (tempTorrent.size.contains("MiB") || tempTorrent.size.contains("MB")) {
                torrentSize = (long) (Double.parseDouble(trimSizeStringToValue(tempTorrent)));
            }
        } catch (Exception ignored) {

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
        String normalizedTorrentNameWithSpaces = getNormalizedTorrentStringWithSpaces(torrentName);
        String normalizedSearchNameWithSpaces = getNormalizedTorrentStringWithSpaces(searchName);
        String normalizedSearchName = getNormalizedTorrentString(searchName);

        //check individual words
        List<String> searchWords = Arrays.asList(normalizedSearchNameWithSpaces.split(" "));
        List<String> torrentWords = Arrays.asList(normalizedTorrentNameWithSpaces.split(" "));
        int searchMaxScore = torrentWords.size();
        AtomicInteger matches = new AtomicInteger();
        searchWords.forEach(searchWord -> {
            if (normalizedTorrentName.contains(searchWord)) {
                matches.getAndIncrement();
            }
        });
        double matchScore = (double) matches.get() / (double) searchMaxScore;
        tempTorrent.searchRating += matchScore;
        tempTorrent.debugRating += String.format("🔍:%.2f (%d/%d)", matchScore, matches.get(), searchMaxScore);

        // bonus for year in torrentName
        if(normalizedTorrentNameWithSpaces.matches(".*[1-2][09][0-9][0-9].*")) {
            tempTorrent.searchRating += 0.5;
            tempTorrent.debugRating += "📅";
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
        // if movie or Series patter +1
        final String name = tempTorrent.name;
        final TorrentType typeOfMedia = determineTypeOfMedia(prepareTorrentName(name));
        if (TorrentType.MOVIES.equals(typeOfMedia)
                || TorrentType.SERIES_SHOWS.equals(typeOfMedia)) {
            tempTorrent.searchRating += 2;
            tempTorrent.debugRating += "🎬";
        }
        if (tempTorrent.isVerified) {
            tempTorrent.searchRating += 0.25;
            tempTorrent.debugRating += "✅";
        }
        if (tempTorrent.cached.size() > 0) {
            tempTorrent.searchRating += 2;
            tempTorrent.debugRating += "🚄: ⚡";
        } else if (seedRatio > 1.0) {
            double seedRating = Math.min(seedRatioOptimized, SEED_RATIO_UPPER_LIMIT) / SEED_RATIO_UPPER_LIMIT;
            tempTorrent.searchRating += seedRating;
            tempTorrent.debugRating += String.format("🚄:%.2f", seedRating);
        } else if (tempTorrent.seeder == 1) {
            tempTorrent.searchRating = tempTorrent.searchRating / 10;
            tempTorrent.debugRating += String.format("🚄: 🐌 %.2f", tempTorrent.searchRating / 10);
        } else {
            tempTorrent.searchRating += seedRatioOptimized;
            tempTorrent.debugRating += String.format("🚄: %.2f", seedRatioOptimized);
        }
        return tempTorrent;
    }

    public static String getNormalizedTorrentString(String name) {
        String lowerCase = name.replaceAll(REGEX_RELEASE_GROUP, "").toLowerCase();
        return lowerCase.trim()
                .replaceAll(TAG_REGEX, ".")
                .replaceAll(TAG_REGEX, ".")
                .replaceAll(TAG_REGEX, ".")
                .replaceAll(TAG_REGEX, ".")
                .replaceAll("[()]+", "")
                .replaceAll("\\[[A-Za-z0-9. -]*\\]", "")
                .replaceAll("\\s", "")
                .replaceAll("\\.", "")
                ;
    }

    public static String getNormalizedTorrentStringWithSpaces(String name) {
        String lowerCase = name.replaceAll(REGEX_RELEASE_GROUP, "").toLowerCase();
        return lowerCase.trim()
                .replaceAll("\\[[a-z0-9. -]*\\]", "")
                .replaceAll(TAG_REGEX, ".")
                .replaceAll(TAG_REGEX, ".")
                .replaceAll(TAG_REGEX, ".")
                .replaceAll(TAG_REGEX, ".")
                .replaceAll("\\.", " ").trim();
    }

    public static String getNormalizedTorrentStringWithSpacesKeepCase(String name) {
        if(name == null) return null;
        String string = name.replaceAll(REGEX_RELEASE_GROUP, "").toLowerCase();
        return string.trim()
                .replaceAll("[()]+", "")
                .replaceAll("\\[[A-Za-z0-9. -]*\\]", "")
                .replaceAll("\\.", " ").trim();
    }

    public static String cleanNumberString(String value) {
        return value.replaceAll(",", "");
    }

    public static boolean isBlocklisted(Torrent torrent) {
        return torrent.name != null && (
                torrent.name.toLowerCase().contains("telesync") ||
                        torrent.name.toLowerCase().contains("telecine") ||
                        torrent.name.toLowerCase().contains(" hdcam") ||
                        torrent.name.toLowerCase().contains("tscam") ||
                        torrent.name.toLowerCase().contains(".cam.") ||
                        torrent.name.toLowerCase().contains("cam-rip") ||
                        torrent.name.toLowerCase().contains("camrip") ||
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
                !isBlocklisted(torrent);
    }

    public static boolean isValidMetaTorrent(Torrent torrent) {
        return torrent.name != null &&
                torrent.seeder > 0 &&
                !isBlocklisted(torrent);
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

    public static TorrentType determineTypeOfMedia(String cleanedString) {
        if (cleanedString.matches(".*[ ._-]+[re]*dump[ ._-]+.*") || cleanedString.matches(".*\\s[pP][dD][fF].*") || cleanedString.matches(".*\\s[eE][pP][uU][bB].*")) {
            return TorrentType.TRANSFER;
        } else if (cleanedString.matches("(.+[ .]+s[0-9]+.+)|(.+season.+)")) {
            return TorrentType.SERIES_SHOWS;
        } else if (isMovieString(cleanedString)) {
            return TorrentType.MOVIES;
        }
        return TorrentType.TRANSFER;
    }

    public static boolean isMovieString(String string) {
        return string.matches(".*([xXhH]26[4-5]|[xX][vV][iI][dD]|[1-2][0-9]{3}[^0-9p\\/M\\@]*).*");
    }

    public static String prepareTorrentName(String torrentName) {
        String normalizedTorrentStringWithSpaces = getNormalizedTorrentStringWithSpacesKeepCase(torrentName);
        return removeReleaseTags(normalizedTorrentStringWithSpaces);
    }

    private static String removeReleaseTags(final String string) {
        StringBuilder releaseTagsRemoved = new StringBuilder(string);
        torrentService.getReleaseTags().forEach(tag -> {
            String temporaryString = releaseTagsRemoved.toString().replaceAll("\\s(?i)" + tag + "\\s", " ");
            releaseTagsRemoved.setLength(0);
            releaseTagsRemoved.append(temporaryString);
        });
        return releaseTagsRemoved.toString();
    }

    public static String humanReadableByteCountBinary(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.2f %ciB", value / 1024.0, ci.current());
    }

}

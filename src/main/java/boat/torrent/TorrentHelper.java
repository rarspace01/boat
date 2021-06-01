package boat.torrent;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
        try {
            if (tempTorrent.size.contains("GiB") || tempTorrent.size.contains("GB")) {
                torrentSize = (long) (Double.parseDouble(trimSizeStringToValue(tempTorrent)) * 1024);
            } else if (tempTorrent.size.contains("MiB") || tempTorrent.size.contains("MB")) {
                torrentSize = (long) (Double.parseDouble(trimSizeStringToValue(tempTorrent)));
            }
        } catch (Exception ignored) {

        }
        return torrentSize;
    }

    public static double extractTorrentSizeFromString(String sizeString) {
        long torrentSize = 0;
        try {
            if (sizeString.contains("GiB") || sizeString.contains("GB")) {
                torrentSize = (long) (Double.parseDouble(trimSizeStringToValue(sizeString)) * 1024);
            } else if (sizeString.contains("MiB") || sizeString.contains("MB")) {
                torrentSize = (long) (Double.parseDouble(trimSizeStringToValue(sizeString)));
            }
        } catch (Exception ignored) {

        }
        return torrentSize;
    }

    private static String trimSizeStringToValue(Torrent tempTorrent) {
        return tempTorrent.size.replaceAll("(GiB)|(GB)|(MiB)|(MB)|(<.*?>)", "").trim();
    }

    private static String trimSizeStringToValue(String stringValue) {
        return stringValue.replaceAll("(GiB)|(GB)|(MiB)|(MB)|(<.*?>)", "").trim();
    }

    public static Torrent evaluateRating(Torrent tempTorrent, String searchName) {
        tempTorrent.debugRating = "";
        tempTorrent.searchRating = 0.0;

        String debugAdditional = "";

        String torrentName = tempTorrent.name;

        double additionalRating = 0.0;

        if (torrentName == null || torrentName.trim().length() == 0) {
            torrentName = "";
        }

        String normalizedTorrentName = getNormalizedTorrentString(torrentName);
        String normalizedTorrentNameWithSpaces = getNormalizedTorrentStringWithSpaces(torrentName);
        String normalizedSearchNameWithSpaces = getNormalizedTorrentStringWithSpaces(searchName);
        String normalizedSearchName = getNormalizedTorrentString(searchName);

        //check individual words
        List<String> searchWords = Arrays.asList(normalizedSearchNameWithSpaces.split(" "));
        List<String> torrentWords = Arrays.asList(normalizedTorrentNameWithSpaces.split(" "));
        int torrentWordCount = torrentWords.size();
        AtomicInteger matches = new AtomicInteger();
        searchWords.forEach(searchWord -> {
            if (normalizedTorrentName.contains(searchWord)) {
                matches.getAndIncrement();
            }
        });
        double matchedScoreOfTorrent = (double) matches.get() / (double) torrentWordCount;
        double matchedScoreOfSearch = (double) matches.get() / (double) searchWords.size();

        // bonus for year in torrentName
        if (normalizedTorrentNameWithSpaces.matches(".*[1-2][09][0-9][0-9].*")) {
            additionalRating += 0.5;
            debugAdditional += "📅";
        }

        // calc first range
        double sizeRating = Math.min(tempTorrent.lsize, SIZE_UPPER_LIMIT) / SIZE_UPPER_LIMIT;
        // calculate seeder ratio
        double seedRatio = (double) tempTorrent.seeder / Math.max((double) tempTorrent.leecher, 0.1);
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
            additionalRating += 1;
            debugAdditional += "🎬";
        }
        if (tempTorrent.isVerified) {
            additionalRating += 0.25;
            debugAdditional += "✅";
        }
        double speedRating;
        if (tempTorrent.cached.size() > 0) {
            debugAdditional += "⚡";
            speedRating = 2;
        } else if (seedRatio > 1.0) {
            speedRating = Math.min(seedRatioOptimized, SEED_RATIO_UPPER_LIMIT) / SEED_RATIO_UPPER_LIMIT;
        } else if (tempTorrent.seeder == 1) {
            speedRating = seedRatioOptimized / 10.0;
        } else {
            speedRating = seedRatioOptimized;
        }

        // searchRatingNew Calc
        tempTorrent.searchRating =
            matchedScoreOfSearch * (matchedScoreOfTorrent + sizeRating + speedRating + additionalRating);

        tempTorrent.debugRating = String
            .format("🔍%.2f * (🔦%.2f + 📦%.2f + 🚄%.2f + 🧮%.2f - %s)", matchedScoreOfSearch, matchedScoreOfTorrent,
                sizeRating,
                speedRating, additionalRating, debugAdditional);

        return tempTorrent;
    }

    public static String getNormalizedTorrentString(String name) {
        String lowerCase = name.replaceAll(REGEX_RELEASE_GROUP, "").toLowerCase();
        return lowerCase.trim()
            .replaceAll("['`´!]", "")
            .replaceAll(",", ".")
            .replaceAll("\\[[A-Za-z0-9. -]*\\]", "")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll("[()]+", "")
            .replaceAll("\\s", "")
            .replaceAll("\\.", "")
            ;
    }

    public static String getNormalizedTorrentStringWithSpaces(String name) {
        String lowerCase = name.replaceAll(REGEX_RELEASE_GROUP, "").toLowerCase().replaceAll("\"", "");
        return lowerCase.trim()
            .replaceAll("['`´!]", "")
            .replaceAll("\\[[a-z0-9. -]*\\]", "")
            .replaceAll(",", ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll("[()]+", "")
            .replaceAll("\\.", " ").trim();
    }

    public static String getNormalizedTorrentStringWithSpacesKeepCase(String name) {
        if (name == null) {
            return null;
        }
        String string = name.replaceAll(REGEX_RELEASE_GROUP, "").toLowerCase().replaceAll("\"", "");
        return string.trim()
            .replaceAll("\\[[A-Za-z0-9. -]*\\]", "")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll(TAG_REGEX, ".")
            .replaceAll("\\.", " ").trim();
    }

    public static String cleanNumberString(String value) {
        return value.replaceAll(",", "");
    }

    public static boolean isBlocklisted(Torrent torrent) {
        if (torrent.name == null) {
            return false;
        }
        final String torrentNameLowerCased = torrent.name.toLowerCase();
        return torrentNameLowerCased.contains("telesync") ||
            torrentNameLowerCased.contains("telecine") ||
            torrentNameLowerCased.contains(" hdcam") ||
            torrentNameLowerCased.contains("tscam") ||
            torrentNameLowerCased.contains(".cam.") ||
            torrentNameLowerCased.contains(" cam ") ||
            torrentNameLowerCased.contains("cam-rip") ||
            torrentNameLowerCased.contains("camrip") ||
            torrentNameLowerCased.contains(".hdcam.") ||
            torrentNameLowerCased.contains(" hdts") ||
            torrentNameLowerCased.contains(" hd-ts") ||
            torrentNameLowerCased.contains(".hd-ts") ||
            torrentNameLowerCased.contains(".hdtc.") ||
            torrentNameLowerCased.contains(".ts.") ||
            torrentNameLowerCased.contains("[ts]") ||
            torrentNameLowerCased.contains("pdvd") ||
            torrentNameLowerCased.contains("predvdrip") ||
            torrentNameLowerCased.contains("workprint") ||
            torrentNameLowerCased.contains("xxx") ||
            torrentNameLowerCased.contains("porn") ||
            torrentNameLowerCased.contains(".hdts.");
    }

    public static boolean isValidTorrent(Torrent torrent, boolean validateUri) {
        return torrent.name != null &&
            (validateUri ? torrent.magnetUri != null : true) &&
            torrent.seeder > 0 &&
            !isBlocklisted(torrent) &&
            torrent.lsize > 0
            ;
    }

    public static boolean isValidTorrent(Torrent torrent) {
        return isValidTorrent(torrent, true);
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
            + torrentService.getTrackerUrls().stream().map(TorrentHelper::urlEncode)
            .collect(Collectors.joining("&tr=", "&tr=", ""));
    }

    public static TorrentType determineTypeOfMedia(String string) {
        String cleanedString = string.toLowerCase();
        if (cleanedString.matches(".*[ ._-]+[re]*dump[ ._-]+.*") || cleanedString.matches(".*\\.[pP][dD][fF].*")
            || cleanedString.matches(".*\\.[eE][pP][uU][bB].*")) {
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

    public static TorrentType determineTypeOfMedia(List<TorrentFile> filesFromTorrent) {
        Map<TorrentType, Integer> countMap = new HashMap<>();
        filesFromTorrent
            .forEach(torrentFile -> countMap.compute(determineTypeOfMedia(torrentFile.name), (torrentType, integer) ->
                integer == null ? 1 : integer + 1));
        final Optional<Entry<TorrentType, Integer>> maxEntry = countMap.entrySet()
            .stream()
            .max(Entry.comparingByValue());
        return maxEntry.isPresent() ? maxEntry.get().getKey() : TorrentType.TRANSFER;
    }
}

package hello.torrent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TorrentHelper {

    public static final double SIZE_UPPER_LIMIT = 15000.0;
    public static final double SEED_RATIO_UPPER_LIMIT = 3.0;
    public static final Comparator<Torrent> torrentSorter = (o1, o2) -> {
        if (o1.searchRating > o2.searchRating) {
            return -1;
        } else if (o1.searchRating < o2.searchRating) {
            return 1;
        } else {
            return Double.compare(o2.lsize, o1.lsize);
        }
    };

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

    public static void evaluateRating(Torrent tempTorrent, String searchName) {
        String torrentName = tempTorrent.name;
        if (torrentName == null || torrentName.trim().length() == 0) {
            return;
        }

        String normalizedTorrentName = getNormalizedTorrentString(torrentName);
        String normalizedSearchName = getNormalizedTorrentString(searchName);

        if (normalizedTorrentName.contains(normalizedSearchName.trim().toLowerCase())) {
            tempTorrent.searchRating += 1;
            tempTorrent.debugRating += "🔍🔍";
        }
        //check indivdual words
        List<String> searchWords = Arrays.asList(searchName.trim().toLowerCase().split(" "));
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
        if (seedRatio > 1.0) {
            double seedRating = Math.min(seedRatio, SEED_RATIO_UPPER_LIMIT) / SEED_RATIO_UPPER_LIMIT;
            tempTorrent.searchRating += seedRating;
            tempTorrent.debugRating += String.format("🚄:%.2f", seedRating);
        }
        if (tempTorrent.seeder == 1) {
            tempTorrent.searchRating = tempTorrent.searchRating / 10;
            tempTorrent.debugRating += String.format("!🚄 OVR:%.2f", tempTorrent.searchRating / 10);
            ;
        }
    }

    public static String getNormalizedTorrentString(String name) {
        String lowerCase = name.replaceAll("(-[A-Z]+)", "").toLowerCase();
        return lowerCase.trim()
                .replaceAll("(ac3|x264|h264|h265|x265|mp3|hdrip|mkv|mp4|xvid|divx|web|720p|1080p|4K|UHD|\\s|\\.)", "")
                .replaceAll("[()]+", "")
                .replaceAll("\\[[A-Za-z0-9. -]*\\]", "")
                .replaceAll("\\.", "");
    }

    public static String cleanNumberString(String value) {
        return value.replaceAll(",", "");
    }

    public static boolean isBlacklisted(Torrent torrent) {
        return torrent.name != null && (
                torrent.name.toLowerCase().contains("telesync") ||
                        torrent.name.toLowerCase().contains("telecine") ||
                        torrent.name.toLowerCase().contains(" hdcam") ||
                        torrent.name.toLowerCase().contains(" hdts") ||
                        torrent.name.toLowerCase().contains(" hd-ts") ||
                        torrent.name.toLowerCase().contains(".hdtc.") ||
                        torrent.name.toLowerCase().contains("tscam") ||
                        torrent.name.toLowerCase().contains(".ts.") ||
                        torrent.name.toLowerCase().contains("[ts]") ||
                        torrent.name.toLowerCase().contains(".hdts.") ||
                        torrent.name.toLowerCase().contains(".cam.") ||
                        torrent.name.toLowerCase().contains(".hdcam.")
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
}

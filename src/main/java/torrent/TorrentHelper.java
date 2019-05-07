package torrent;

import java.util.Comparator;

public class TorrentHelper {

    public static final String REGEX_CLEAN_NAME = "[-+ .]";
    public static final double SIZE_FIRST_LIMIT = 1024.0;
    public static final Comparator torrentSorter = (Comparator<Torrent>) (o1, o2) -> {
        if (o1.searchRating > o2.searchRating) {
            return -1;
        } else if (o1.searchRating < o2.searchRating) {
            return 1;
        } else {
            return Double.compare(o2.lsize, o1.lsize);
        }
    };
    private static final double SIZE_SECOND_LIMIT = 25 * 1024.0;

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

    public static void evaluateRating(Torrent tempTorrent, String torrentname) {
        if (tempTorrent.name.toLowerCase().replaceAll(REGEX_CLEAN_NAME, "").contains(torrentname.toLowerCase().replaceAll(REGEX_CLEAN_NAME, ""))) {
            tempTorrent.searchRating += 2;
        }
        // calc first range
        tempTorrent.searchRating += Math.max(tempTorrent.lsize, SIZE_FIRST_LIMIT) / SIZE_FIRST_LIMIT;
        if (tempTorrent.lsize > SIZE_FIRST_LIMIT) {
            tempTorrent.searchRating += ((Math.max(tempTorrent.lsize, SIZE_SECOND_LIMIT) - SIZE_FIRST_LIMIT) / (SIZE_SECOND_LIMIT - SIZE_FIRST_LIMIT));
        }
        if (tempTorrent.seeder > 30) {
            tempTorrent.searchRating++;
        }
    }
}

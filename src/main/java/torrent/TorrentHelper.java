package torrent;

import java.util.Comparator;

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

    public static void evaluateRating(Torrent tempTorrent, String torrentname) {
        if (getNormalizedTorrentString(tempTorrent.name).contains(getNormalizedTorrentString(torrentname))) {
            tempTorrent.searchRating += 1;
            // determine closeness
            double closenessFactor = (double) getNormalizedTorrentString(torrentname).length() / (double) getNormalizedTorrentString(torrentname).length();
            tempTorrent.searchRating += closenessFactor;
        }
        // calc first range
        tempTorrent.searchRating += Math.min(tempTorrent.lsize, SIZE_UPPER_LIMIT) / SIZE_UPPER_LIMIT;
        // calculate seeder ratio
        double seedRatio = (double) tempTorrent.seeder / (double) tempTorrent.leecher;
        if (seedRatio > 1.0) {
            tempTorrent.searchRating += Math.min(seedRatio, SEED_RATIO_UPPER_LIMIT) / SEED_RATIO_UPPER_LIMIT;
        }
    }

    public static String getNormalizedTorrentString(String name) {
        String lowerCase = name.toLowerCase();
        return lowerCase.trim().replaceAll("(ac3|x264|h265|x265|mp3|hdrip|mkv|mp4|xvid|divx|web|720p|1080p|\\s|\\.)", "").replaceAll("(-[\\S]+)", "").replaceAll("\\.", "");
    }
}

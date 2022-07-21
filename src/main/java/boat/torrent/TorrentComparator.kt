package boat.torrent

class TorrentComparator {
    companion object : Comparator<Torrent> {
        override fun compare(o1: Torrent, o2: Torrent): Int {
            return if (o1.searchRating > o2.searchRating) {
                -1
            } else if (o1.searchRating < o2.searchRating) {
                1
            } else {
                o2.sizeInMB.compareTo(o1.sizeInMB)
            }
        }
    }
}
package boat.torrent;

public enum TorrentType {
    MOVIES("Movies"),
    SERIES_SHOWS("Series-Shows"),
    TRANSFER("transfer");

    public String getType() {
        return type;
    }

    private final String type;

    private TorrentType(String type) {
        this.type = type;
    }

}

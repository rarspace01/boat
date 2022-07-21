package boat.torrent;

import lombok.ToString;

@ToString
public class TorrentFile {

    public String id;
    public String name;
    public long filesize;
    public String url;
}

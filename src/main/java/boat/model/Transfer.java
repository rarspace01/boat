package boat.model;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.annotation.Id;

import boat.torrent.TorrentStatus;
import lombok.Data;

@Data
public class Transfer {

    @Id
    public String id;

    public String remoteId;

    public String source;

    public String uri;

    public TorrentStatus torrentStatus = TorrentStatus.NONE;

    public Double progressInPercentage = 0.0;

    public String feedbackMessage;

    public Duration eta;

    public Instant updated;

}

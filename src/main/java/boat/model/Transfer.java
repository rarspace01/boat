package boat.model;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.annotation.Id;

import lombok.Data;

@Data
public class Transfer {

    @Id
    public String id;

    public String remoteId;

    public String source;

    public String uri;

    public TransferStatus transferStatus = TransferStatus.NONE;

    public TransferType transfertType = TransferType.TORRENT;

    public Double progressInPercentage = 0.0;

    public String feedbackMessage;

    public Duration eta;

    public Instant updated;

    @Override
    public String toString(){
        return String.format("\n[%s,%s,%s,%s,%s,%.3f,%s,%s,%s]", id, remoteId, source, transferStatus, transfertType, progressInPercentage, feedbackMessage, eta,
            updated);
    }

}

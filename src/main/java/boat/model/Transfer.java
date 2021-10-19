package boat.model;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.annotation.Id;

import lombok.Data;

@Data
public class Transfer {

    @Id
    public String id;
    
    public String name;

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
    public String toString() {
        return String.format("\n<br/>[<!-- ID:[%s]RID:[%s] -->%s,\uD83C\uDFE0%s,%s,%s,%.3f,<!-- MSG: %s -->%s <!-- ,%s -->]", id, remoteId, name,
            source.charAt(0), transferStatus, transfertType, progressInPercentage, feedbackMessage,
            printDuration(),
            updated);
    }

    private String printDuration() {
        if(eta == null || eta.equals(Duration.ZERO)) { return ""; } else return eta.toString();
    }

}

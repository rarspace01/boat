package boat.torrent;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import boat.model.TransferStatus;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class Torrent implements Comparable<Torrent> {

    public String name;
    public Date date;
    //public long size;
    public String size;
    public double lsize;
    public int seeder = -1;
    public int leecher = -1;
    public String magnetUri;
    public String category;
    public double searchRating = 0.0;
    public String debugRating = "";
    public String remoteStatusText;
    public int remoteStatusCode;
    public TransferStatus remoteTransferStatus;
    public TransferStatus localTransferStatus;
    public String remoteProgress;
    public double remoteProgressInPercent;
    public String progress;
    public Duration eta;
    public String remoteId;
    public String folder_id;
    public String file_id;
    public String remoteUrl;
    public String source;
    public List<String> cached = new ArrayList<>();
    public List<TorrentFile> fileList = new ArrayList<>();
    public boolean isVerified = false;
    public boolean statsVerified = false;
    private Pattern magnetPattern = Pattern.compile("(btih:)([a-zA-Z0-9]*)&*");
    private Pattern magnetNamePattern = Pattern.compile("dn=(.*?)&");

    public Torrent(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        double seedRatio;

        if (this.leecher > 0) {
            seedRatio = (double) this.seeder / (double) this.leecher;
        } else {
            seedRatio = this.seeder;
        }
        String magnetUriBase64 = "";
        if (magnetUri != null) {
            magnetUriBase64 = Base64.getUrlEncoder().encodeToString(magnetUri.getBytes(StandardCharsets.UTF_8));
        }

        stringBuilder.append(String.format("[%s]\uD83C\uDFE0[%s]", this.name, retrieveSourceName()));

        if (!isRemoteTorrent()) {
            stringBuilder.append(String.format("[%s][%s/%s@%.2f]", size, leecher, seeder, seedRatio));
            stringBuilder.append(String.format("R: %.2f ", this.searchRating));
        }

        if (isNotARemoteTorrent(magnetUriBase64)) {
            stringBuilder.append("<a href=\"./boat/download/?d=").append(magnetUriBase64).append("\">Download</a>");
        }

        if (Strings.isNotEmpty(this.debugRating)) {
            stringBuilder.append(" 🏭").append(this.debugRating);
        }


        /*        if (getTorrentId() != null) {
            stringBuilder.append(" TID:" + getTorrentId());
        }*/
        if (this.remoteStatusText != null && this.remoteProgress != null) {
            String progress = "/" + this.remoteProgress;
            if (remoteStatusText.contains("Uploading")) {
                progress = "";
            }
            stringBuilder.append(" ").append(this.remoteStatusText.replaceAll("finished", "Waiting for Upload"))
                .append(progress);
        }
        if (eta != null) {
            stringBuilder.append(" ETA:").append(this.eta);
        }
        if (!isNotARemoteTorrent(magnetUriBase64)) {
            stringBuilder.append(String.format("%s - %s - folder_id: %s file_id: %s", this.getTorrentId(), this.remoteId, this.folder_id, this.file_id));
        }
        stringBuilder.append("</br>");

        return stringBuilder.toString();
    }

    private boolean isNotARemoteTorrent(String magnetUriBase64) {
        return magnetUriBase64 != null && magnetUriBase64.length() > 0 && this.remoteStatusText == null;
    }

    private String retrieveSourceName() {
        return isRemoteTorrent() && source != null ? String.valueOf(source.charAt(0)) : source;
    }

    private boolean isRemoteTorrent() {
        return remoteId != null && remoteId.length() > 0;
    }

    @Override
    public boolean equals(Object obj) {
        return Objects.equals(this.getTorrentId(), obj != null ? ((Torrent) obj).getTorrentId() : null);
    }

    public String getTorrentId() {
        if (this.magnetUri == null) {
            return getRemoteIdOrHash();
        }
        Matcher matcher = magnetPattern.matcher(this.magnetUri);
        if (matcher.find()) {
            return matcher.group(2).toLowerCase();
        } else {
            return getRemoteIdOrHash();
        }
    }

    public String getTorrentNameFromUri() {
        if (this.magnetUri == null) {
            return null;
        }
        Matcher matcher = magnetNamePattern.matcher(this.magnetUri);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        } else {
            return null;
        }
    }

    @NotNull
    private String getRemoteIdOrHash() {
        return Objects.requireNonNullElseGet(this.remoteId, () -> String.valueOf(this.hashCode()));
    }

    @Override
    public int compareTo(@NotNull Torrent o) {
        return this.lsize < o.lsize ? -1 : 1;
    }
}

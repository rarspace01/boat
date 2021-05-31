package boat.torrent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class Torrent implements Comparable<Torrent> {

    public String name;
    public Date date;
    //public long size;
    public String size;
    public double lsize;
    public int seeder;
    public int leecher;
    public String magnetUri;
    public String category;
    public double searchRatingOld = 0.0;
    public double searchRating = 0.0;
    public String debugRatingOld = "";
    public String debugRating = "";
    public String status;
    public String progress;
    public String eta;
    public String remoteId;
    public String folder_id;
    public String file_id;
    public String remoteUrl;
    public String source;
    public List<String> cached = new ArrayList<>();
    public List<TorrentFile> fileList = new ArrayList<>();
    public boolean isVerified = false;

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

        stringBuilder.append("[" + this.name + "]");
        if (!"Premiumize".equals(source)) {
            stringBuilder.append(String.format("[%s][%s/%s@%.2f]", size, leecher, seeder, seedRatio));
            stringBuilder.append(String.format("R: %.2f NR: %.2f ", this.searchRatingOld, this.searchRating));
        }

        if (magnetUriBase64 != null && magnetUriBase64.length() > 0 && this.status == null) {
            stringBuilder.append("<a href=\"./boat/download/?d=" + magnetUriBase64 + "\">Download</a>");
        }

        if (Strings.isNotEmpty(this.debugRating)) {
            stringBuilder.append(" 🏭" + this.debugRating + " \uD83C\uDFE0" + this.source);
        }


        /*        if (getTorrentId() != null) {
            stringBuilder.append(" TID:" + getTorrentId());
        }*/
        if (this.status != null && this.progress != null) {
            String progress = "/" + this.progress;
            if (status.contains("Uploading")) {
                progress = "";
            }
            stringBuilder.append(" " + this.status.replaceAll("finished", "Waiting for Upload") + progress);
        }
        if (eta != null) {
            stringBuilder.append(" ETA:" + this.eta);
        }

        stringBuilder.append("</br>");

        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return Objects.equals(this.getTorrentId(), obj != null ? ((Torrent) obj).getTorrentId() : null);
    }

    public String getTorrentId() {
        if (this.magnetUri == null) {
            return getRemoteIdOrHash();
        }
        Pattern magnetPattern = Pattern.compile("(btih:)([a-zA-Z0-9]*)&*");
        Matcher matcher = magnetPattern.matcher(this.magnetUri);
        if (matcher.find()) {
            return matcher.group(2).toLowerCase();
        } else {
            return getRemoteIdOrHash();
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

package hello.torrent;

import org.apache.logging.log4j.util.Strings;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Torrent {

    public String name;
    public Date date;
    //public long size;
    public String size;
    public double lsize;
    public int seeder;
    public int leecher;
    public String magnetUri;
    public String category;
    public double searchRating = 0.0;
    public String debugRating = "";
    public String status;
    public String progress;
    public String eta;
    public String remoteId;
    public String folder_id;
    public String file_id;
    public String remoteUrl;

    @Override
    public String toString() {
        DecimalFormat df = new DecimalFormat("#.##");
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

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("[" + this.name + "]");
        if (this.size != null) {
            stringBuilder.append("[" + this.size + "][" + this.leecher + "/" + this.seeder + "@" + df.format(seedRatio) + "]");
            stringBuilder.append("R:" + df.format(this.searchRating) + " ");
        }

        if (magnetUriBase64 != null && magnetUriBase64.length() > 0) {
            stringBuilder.append("<a href=\"./boat/download/?d=" + magnetUriBase64 + "\">Download</a>");
        }

        if (Strings.isNotEmpty(this.debugRating)) {
            stringBuilder.append(" 🏭" + this.debugRating);
        }


//        if (remoteId != null) {
//            stringBuilder.append(" RID:" + remoteId);
//        }
        if (this.status != null && this.progress != null) {
            stringBuilder.append(" " + this.status + "/" + this.progress);
        }
        if (eta != null) {
            stringBuilder.append(" ETA:" + this.eta);
        }

        stringBuilder.append("</br>");

        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return this.getTorrentId().equals(((Torrent) obj).getTorrentId());
    }

    public String getTorrentId() {
        if (this.magnetUri == null) {
            return String.valueOf(this.hashCode());
        }
        Pattern magnetPattern = Pattern.compile("(btih:)([a-z0-9]*)(&dn)");
        Matcher matcher = magnetPattern.matcher(this.magnetUri);
        if (matcher.find()) {
            return matcher.group(2);
        } else {
            return String.valueOf(this.hashCode());
        }
    }
}
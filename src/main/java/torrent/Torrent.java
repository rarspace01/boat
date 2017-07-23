package torrent;

import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.Date;

/**
 * Created by denis on 02/10/2016.
 */
public class Torrent {

    public String name;
    public Date date;
    //public long size;
    public String size;
    public long lsize;
    public int seeder;
    public int leecher;
    public String magnetUri;
    public String category;
    public int searchRating = 0;
    public String status;
    public String progress;
    public String remoteId;

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
        try {
            magnetUriBase64 = Base64.getUrlEncoder().encodeToString(magnetUri.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return "[" + this.name + "][" + this.size + "][" + this.leecher + "/" + this.seeder + "@" + df.format(seedRatio) + "] R:" + this.searchRating + " <a href=\"./boat/download/?d=" + magnetUriBase64 + "\">Download</a> RID: " + this.remoteId + " Status/Progress:" + this.status + "/" + this.progress + "<br/>";
    }
}

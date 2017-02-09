package torrent;

import java.text.DecimalFormat;
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
        DecimalFormat df = new DecimalFormat("#.###");
        double seedRatio;

        if (this.leecher > 0) {
            seedRatio = (double) this.seeder / (double) this.leecher;
        } else {
            seedRatio = this.seeder;
        }

        return "[" + this.name + "][" + this.size + "][" + this.leecher + "/" + this.seeder + "@" + df.format(seedRatio) + "] R:" + this.searchRating + " magnet-uri: " + this.magnetUri + " RID: " + this.remoteId + " Status/Progress:" + this.status + "/" + this.progress;
    }
}

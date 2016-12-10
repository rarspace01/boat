package torrent;

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


}

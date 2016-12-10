package torrent;

import utilities.HttpHelper;
import utilities.PropertiesHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by denis on 24/11/2016.
 */
public class Premiumize {

    private List<Torrent> torrentList = new ArrayList<Torrent>();

    String addTorrentToQueue(Torrent toBeAddedTorrent) {
        String response = "";
        response = HttpHelper.getPage("https://www.premiumize.me/api/transfer/create?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin") +
                "&type=torrent&src=agnet:?xt=urn:btih:" + toBeAddedTorrent.magnetUri);
        return response;
    }

    ArrayList<Torrent> getRemoteTorrents() {

        ArrayList<Torrent> remoteTorrentList = new ArrayList<Torrent>();

        //https://www.premiumize.me/api/transfer/list

        return remoteTorrentList;
    }

}

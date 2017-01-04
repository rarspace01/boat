package torrent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import utilities.HttpHelper;
import utilities.PropertiesHelper;

import java.io.IOException;
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

        String responseTorrents = "";
        responseTorrents = HttpHelper.getPage("https://www.premiumize.me/api/transfer/list?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));



        System.out.println("getRemoteTorrents URL: " + "https://www.premiumize.me/api/transfer/list?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));
        System.out.println("getRemoteTorrents: " + responseTorrents);

        parseRemoteTorrents(responseTorrents);


        return remoteTorrentList;
    }

    private void parseRemoteTorrents(String pageContent){

        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode rootNode = m.readTree(pageContent);

            JsonNode localNodes = rootNode.path("transfers");

            for(JsonNode localNode:localNodes){
                System.out.println(localNode.get("name") + "["+ localNode.get("hash") +"]" + "Status" + localNode.get("status") + localNode.get("progress")+"/100 " + Long.parseLong(localNode.get("size").toString())/(1024L*1024L) + "MB");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}

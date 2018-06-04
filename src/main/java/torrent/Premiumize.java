package torrent;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    public String addTorrentToQueue(Torrent toBeAddedTorrent) {
        String response = "";
        String addTorrenntUrl = "https://www.premiumize.me/api/transfer/create?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin") +
                "&type=torrent&src=" + toBeAddedTorrent.magnetUri;
        response = HttpHelper.getPage(addTorrenntUrl);
        System.out.println("GET: " + addTorrenntUrl);
        return response;
    }

    public ArrayList<Torrent> getRemoteTorrents() {

        ArrayList<Torrent> remoteTorrentList = new ArrayList<Torrent>();

        String responseTorrents = "";
        responseTorrents = HttpHelper.getPage("https://www.premiumize.me/api/transfer/list?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));


        System.out.println("getRemoteTorrents URL: " + "https://www.premiumize.me/api/transfer/list?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));
        System.out.println("getRemoteTorrents: " + responseTorrents);

        remoteTorrentList = parseRemoteTorrents(responseTorrents);


        return remoteTorrentList;
    }

    public List<TorrentFile> getFilesFromTorrent(Torrent torrent) {
        List<TorrentFile> returnList = new ArrayList<TorrentFile>();

        // https://www.premiumize.me/api/torrent/browse?hash=HASHID
        String responseFiles = HttpHelper.getPage("https://www.premiumize.me/api/torrent/browse?hash=" + torrent.remoteId +
                "&customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));

        System.out.println(responseFiles);

        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode rootNode = m.readTree(responseFiles);

            JsonNode localNodes = rootNode.path("content");

            List<JsonNode> fileList = localNodes.findParents("type");

            for (JsonNode jsonFile : fileList) {

                if (jsonFile.get("type").asText().equals("file")) {

                    TorrentFile tf = new TorrentFile();

                    tf.name = jsonFile.get("name").asText();
                    tf.filesize = jsonFile.get("size").asLong();
                    tf.url = jsonFile.get("url").asText();

                    returnList.add(tf);
                }
            }

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        return returnList;
    }

    private ArrayList<Torrent> parseRemoteTorrents(String pageContent) {

        ArrayList<Torrent> remoteTorrentList = new ArrayList<Torrent>();

        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode rootNode = m.readTree(pageContent);

            JsonNode localNodes = rootNode.path("transfers");

            for (JsonNode localNode : localNodes) {

                System.out.println(localNode.toString());

                Torrent tempTorrent = new Torrent();

                tempTorrent.name = localNode.get("name").toString().replace("\"", "");

                tempTorrent.remoteId = localNode.get("id").toString().replace("\"", "");
                //tempTorrent.lsize = Long.parseLong(localNode.get("size").toString());
                tempTorrent.status = localNode.get("status").toString().replace("\"", "");

                tempTorrent.progress = localNode.get("progress").toString();

                remoteTorrentList.add(tempTorrent);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return remoteTorrentList;

    }

}

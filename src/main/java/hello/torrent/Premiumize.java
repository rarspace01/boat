package hello.torrent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import hello.info.TheFilmDataBaseService;
import hello.utilities.HttpHelper;
import hello.utilities.PropertiesHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Premiumize extends HttpUser {

    private final TheFilmDataBaseService theFilmDataBaseService;

    public Premiumize(HttpHelper httpHelper, TheFilmDataBaseService theFilmDataBaseService) {
        super(httpHelper);
        this.theFilmDataBaseService = theFilmDataBaseService;
    }

    public String addTorrentToQueue(Torrent toBeAddedTorrent) {
        String response;
        String addTorrenntUrl = "https://www.premiumize.me/api/transfer/create?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin") +
                "&type=hello.torrent&src=" + cleanMagnetUri(toBeAddedTorrent.magnetUri);
        response = httpHelper.getPage(addTorrenntUrl);
        return response;
    }

    private String cleanMagnetUri(String magnetUri) {
        return magnetUri.replaceAll(" ", "_");
    }

    public ArrayList<Torrent> getRemoteTorrents() {

        ArrayList<Torrent> remoteTorrentList;
        String responseTorrents;
        responseTorrents = httpHelper.getPage("https://www.premiumize.me/api/transfer/list?customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));

        remoteTorrentList = parseRemoteTorrents(responseTorrents);

        return remoteTorrentList;
    }

    public String getMainFileURLFromTorrent(Torrent torrent) {
        List<TorrentFile> tfList = getFilesFromTorrent(torrent);

        String remoteURL = null;

        // iterate over and check for One File Torrent
        long biggestFileYet = 0;
        for (TorrentFile tf : tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize;
                remoteURL = tf.url;
            }
        }
        return remoteURL;
    }

    public List<TorrentFile> getFilesFromTorrent(Torrent torrent) {
        List<TorrentFile> returnList = new ArrayList<>();

        String responseFiles = httpHelper.getPage("https://www.premiumize.me/api/folder/list?id=" + torrent.folder_id +
                "&customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));

        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode rootNode = m.readTree(responseFiles);

            JsonNode localNodes = rootNode.path("content");

            List<JsonNode> fileList = localNodes.findParents("type");

            for (JsonNode jsonFile : fileList) {

                if (jsonFile.get("type").asText().equals("file")) {
                    extractTorrentFileFromJSON(torrent, returnList, jsonFile, "");
                } else if (jsonFile.get("type").asText().equals("folder")) {
                    extractTorrentFilesFromJSONFolder(torrent, returnList, jsonFile, "");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        return returnList;
    }

    private void extractTorrentFilesFromJSONFolder(Torrent torrent, List<TorrentFile> returnList, JsonNode jsonFolder, String prefix) {
        String responseFiles = httpHelper.getPage("https://www.premiumize.me/api/folder/list?id=" + String.valueOf(jsonFolder.get("id").asText()) +
                "&customer_id=" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin"));
        String folderName = prefix + String.valueOf(jsonFolder.get("name").asText()) + "/";

        ObjectMapper m = new ObjectMapper();
        JsonNode rootNode = null;
        try {
            rootNode = m.readTree(responseFiles);
            JsonNode localNodes = rootNode.path("content");
            List<JsonNode> fileList = localNodes.findParents("type");

            for (JsonNode jsonFile : fileList) {

                if (jsonFile.get("type").asText().equals("file")) {
                    extractTorrentFileFromJSON(torrent, returnList, jsonFile, folderName);
                } else if (jsonFile.get("type").asText().equals("folder")) {
                    extractTorrentFilesFromJSONFolder(torrent, returnList, jsonFile, folderName);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void extractTorrentFileFromJSON(Torrent torrent, List<TorrentFile> returnList, JsonNode jsonFile, String prefix) {
        TorrentFile tf = new TorrentFile();
        // check if hello.torrent is onefile and is located in root
        if (torrent.file_id != null && torrent.folder_id != null) {
            if (String.valueOf(jsonFile.get("id").asText()).equals(torrent.file_id)) {
                tf.name = prefix + jsonFile.get("name").asText();
                tf.filesize = jsonFile.get("size").asLong();
                tf.url = jsonFile.get("link").asText();
                returnList.add(tf);
            }
        } else {
            tf.name = prefix + jsonFile.get("name").asText();
            tf.filesize = jsonFile.get("size").asLong();
            tf.url = jsonFile.get("link").asText();
            returnList.add(tf);
        }
    }

    private ArrayList<Torrent> parseRemoteTorrents(String pageContent) {

        ArrayList<Torrent> remoteTorrentList = new ArrayList<>();

        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode rootNode = m.readTree(pageContent);
            JsonNode localNodes = rootNode.path("transfers");

            for (JsonNode localNode : localNodes) {
                Torrent tempTorrent = new Torrent(toString());
                tempTorrent.name = localNode.get("name").asText();
                tempTorrent.folder_id = localNode.get("folder_id").asText();
                tempTorrent.file_id = localNode.get("file_id").asText();
                tempTorrent.folder_id = cleanJsonNull(tempTorrent.folder_id);
                tempTorrent.file_id = cleanJsonNull(tempTorrent.file_id);
                tempTorrent.remoteId = localNode.get("id").toString().replace("\"", "");
                tempTorrent.status = localNode.get("status").asText();
                String[] messages = localNode.get("message").asText().split(",");
                if (messages.length == 3) {
                    tempTorrent.eta = messages[2];
                }
                tempTorrent.progress = localNode.get("progress").toString();
                remoteTorrentList.add(tempTorrent);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return remoteTorrentList;

    }

    private String cleanJsonNull(String inputString) {
        return inputString.equals("null") ? null : inputString;
    }

    public void delete(Torrent remoteTorrent) {
        String removeTorrenntUrl = "https://www.premiumize.me/api/transfer/delete?id=" + remoteTorrent.remoteId + "&" +
                PropertiesHelper.getProperty("customer_id") + "&pin=" + PropertiesHelper.getProperty("pin") +
                "&type=hello.torrent&src=" + remoteTorrent.magnetUri;
        httpHelper.getPage(removeTorrenntUrl);
    }

    public boolean isSingleFileDownload(Torrent remoteTorrent) {
        List<TorrentFile> tfList = getFilesFromTorrent(remoteTorrent);
        // getMaxFilesize
        // getSumSize
        long sumFileSize = 0L;
        long biggestFileYet = 0L;
        for (TorrentFile tf : tfList) {
            if (tf.filesize > biggestFileYet) {
                biggestFileYet = tf.filesize;
            }
            sumFileSize += tf.filesize;
        }
        // if maxfilesize >90% sumSize --> Singlefile
        return biggestFileYet > (0.9d * sumFileSize);
    }

    public List<Torrent> getCacheStateOfTorrents(List<Torrent> torrents) {
        String requestUrl = "https://www.premiumize.me/api/cache/check?" + "apikey=" +
                PropertiesHelper.getProperty("pin") + "%s";
        String urlEncodedBrackets = TorrentHelper.urlEncode("[]");
        String collected = torrents.stream().map(Torrent::getTorrentId).collect(Collectors.joining("&items" + urlEncodedBrackets + "=", "&items" + urlEncodedBrackets + "=", ""));
        String checkUrl = String.format(requestUrl, collected);
        String pageContent = httpHelper.getPage(checkUrl);
        JsonParser parser = new JsonParser();
        JsonElement jsonRoot = parser.parse(pageContent);
        JsonElement reponse = jsonRoot.getAsJsonObject().get("response");
        JsonArray reponseArray = reponse.getAsJsonArray();
        AtomicInteger index= new AtomicInteger();
        if(reponseArray.size() == torrents.size()){
            reponseArray.forEach(jsonElement -> {
                torrents.get(index.get()).isCached = jsonElement.getAsBoolean();
                index.getAndIncrement();
            });
        }
        return torrents;
    }

}

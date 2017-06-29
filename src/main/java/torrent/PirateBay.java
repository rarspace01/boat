package torrent;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import utilities.HttpHelper;
import utilities.PropertiesHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by denis on 02/10/2016.
 */
public class PirateBay implements TorrentSearchEngine {

    private static final int MAX_PAGES = 5;

    //public static void main(String[] args) {
    public PirateBay() {

//        DriveHelper test = new DriveHelper();
//        test.uploadFile(new File(System.getProperty("user.home"),".gitconfig"), "remotetest/remotetest/remotetest");

        System.out.println(PropertiesHelper.getProperty("customer_id"));




        /*//new PirateBay().printResults(resultList);
        Torrent bestTorrent = new PirateBay().suggestATorrent(resultList);
        System.out.println("We should get:");
        System.out.println(bestTorrent);

        // add the torrent to premiumize
        new Premiumize().addTorrentToQueue(bestTorrent);

        System.out.println("Remote Torrents:");

        List<Torrent> remoteList = new Premiumize().getRemoteTorrents();


        for (Torrent torrent : remoteList) {
            PropertiesHelper.writeState(torrent);

            // check if not already downloaded
            if (torrent.status.equals("finished")) {

                System.out.println("Potential Torrent for Downloading found: " + torrent);

                List<TorrentFile> tfList = new Premiumize().getFilesFromTorrent(torrent);

                // iterate over and check for One File Torrent
                for (TorrentFile tf : tfList) {
                    if ((double) tf.filesize > (torrent.lsize * 0.8)) {
                        System.out.println("SBF Torrent: " + tf.name + " -> " + tf.url + " Size: " + (tf.filesize / (1024L * 1024L)) + " MB");

                        // start the download

                        //FileUtils.copyURLToFile(new URL(tf.url), new File("./"+tf.name));
                        System.out.println("Downloaded SBF Torrent: " + tf.name + " -> " + tf.url + " Size: " + (tf.filesize / (1024L * 1024L)) + " MB");

                    }
                }

            }
        }
*/
        //new PirateBay().printResults(remoteList);

    }

    @Override
    public List<Torrent> searchTorrents(String torrentname) {

        int iPageindex = 0;

        CopyOnWriteArrayList<Torrent> torrentList = new CopyOnWriteArrayList<>();

        for (int i = 0; i < MAX_PAGES; i++) {

            System.out.println("P [" + (i + 1) + "/" + MAX_PAGES + "]");

            iPageindex = i;
            String localString = HttpHelper.getPage("https://thepiratebay.org/search/" + torrentname + "/" + iPageindex + "/99/200", null, "lw=s");
            torrentList.addAll(parseTorrentsOnResultPage(localString, torrentname));
        }

        return torrentList;
    }

    @Override
    public Torrent suggestATorrent(List<Torrent> inputList) {
        Torrent returnTorrent = null;

        // sort the findings
        inputList.sort(new Comparator<Torrent>() {
            @Override
            public int compare(Torrent o1, Torrent o2) {

                if (o1.searchRating > o2.searchRating) {
                    return -1;
                } else if (o1.searchRating < o2.searchRating) {
                    return 1;
                } else {
                    if (o1.lsize > o2.lsize) {
                        return -1;
                    } else if (o1.lsize < o2.lsize) {
                        return 1;
                    } else {
                        return 0;
                    }
                }

            }
        });

        if (inputList != null && inputList.size() > 0) {

            int localMax = 0;
            long maxSize = 0;
            int maxSizeIndex = -1;
            int index = 0;

            // get the potentials
            for (Torrent torrent : inputList) {
                if ((torrent.searchRating > localMax) && (torrent.lsize > maxSize)) {
                    localMax = torrent.searchRating;
                    maxSize = torrent.lsize;
                    maxSizeIndex = index;
                } else {
                    break;
                }
                index++;
            }

            if (maxSizeIndex > -1) {
                returnTorrent = inputList.get(maxSizeIndex);
            }
        }
        return returnTorrent;
    }

    private List<Torrent> parseTorrentsOnResultPage(String pageContent, String torrentname) {

        ArrayList<Torrent> torrentList = new ArrayList<>();

        Document doc = Jsoup.parse(pageContent);

        Elements torrentListOnPage = doc.select("tr:not(.header)");

        for (Element torrent : torrentListOnPage) {

            Torrent tempTorrent = new Torrent();

            // extract ahref for title
            Elements nameElements = torrent.select("a[title~=Details for]");
            if (nameElements.size() > 0) {
                tempTorrent.name = nameElements.get(0).text();
            }

            // extract uri for magnetlink
            Elements uriElements = torrent.select("a[title~=using magnet]");
            if (uriElements.size() > 0) {
                tempTorrent.magnetUri = uriElements.get(0).attributes().get("href");
            }

            // extract date

            String inputDateString = torrent.select("td").get(2).text().replace("\u00a0", " ");

            SimpleDateFormat formatter = new SimpleDateFormat("MM-dd yyyy");

            try {
                tempTorrent.date = formatter.parse(inputDateString);
            } catch (ParseException e) {
                e.printStackTrace();
            }

            // extract size
            tempTorrent.size = torrent.select("td").get(4).text().replace("\u00a0", " ");

            if (tempTorrent.size.contains("GiB")) {
                tempTorrent.lsize = (long) (Double.parseDouble(tempTorrent.size.replace("GiB", "").trim()) * 1024);
            } else if (tempTorrent.size.contains("MiB")) {
                tempTorrent.lsize = (long) (Double.parseDouble(tempTorrent.size.replace("MiB", "").trim()));
            }


            // extract seeder
            tempTorrent.seeder = Integer.parseInt(torrent.select("td").get(5).text());

            // extract leecher
            tempTorrent.leecher = Integer.parseInt(torrent.select("td").get(6).text());

            // evaluate result
            if (tempTorrent.name.toLowerCase().replaceAll("[ .]", "").contains(torrentname.toLowerCase().replaceAll("[ .]", ""))) {
                tempTorrent.searchRating += 2;
            }
            if (tempTorrent.lsize > 10 * 1024) {
                tempTorrent.searchRating++;
            } /* else if(tempTorrent.lsize>1300)
            {
                tempTorrent.searchRating++;
            } */
            if (tempTorrent.seeder > 30) {
                tempTorrent.searchRating++;
            }


            // filter torrents without any seeders
            if (tempTorrent.seeder > 0) {
                torrentList.add(tempTorrent);
            }
        }

        return torrentList;

    }

    private void printResults(List<Torrent> torrents) {
        for (Torrent t : torrents) {
            System.out.println(t);
        }
    }

}

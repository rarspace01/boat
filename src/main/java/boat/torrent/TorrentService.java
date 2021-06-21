package boat.torrent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TorrentService {

    private List<String> trackerList = new ArrayList<>();
    private List<String> releaseTags = new ArrayList<>();

    public List<String> getTrackerUrls() {
        if (trackerList.size() > 0) {
            return trackerList;
        }
        final int maxChars = 1000;
        AtomicInteger currentChars = new AtomicInteger(0);
        List<String> trackerList = new ArrayList<>();
        try {
            InputStream inputStream = TorrentService.class.getResourceAsStream("/trackers.txt");
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.defaultCharset());
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (currentChars.get() + line.length() < maxChars) {
                    trackerList.add(line);
                    currentChars.addAndGet(line.length());
                }
            }
            return trackerList;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public List<String> getAllTrackerUrls() {
        if (trackerList.size() > 0) {
            return trackerList;
        }
        AtomicInteger currentChars = new AtomicInteger(0);
        List<String> trackerList = new ArrayList<>();
        try {
            InputStream inputStream = TorrentService.class.getResourceAsStream("/trackers.txt");
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.defaultCharset());
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                trackerList.add(line);
            }
            return trackerList;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public List<String> getReleaseTags() {
        if (releaseTags.size() > 0) {
            return releaseTags;
        }
        List<String> releaseTags = new ArrayList<>();
        try {
            InputStream inputStream = TorrentService.class.getResourceAsStream("/releasetags.txt");
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.defaultCharset());
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                    releaseTags.add(line.toLowerCase());
            }
            return releaseTags;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

}

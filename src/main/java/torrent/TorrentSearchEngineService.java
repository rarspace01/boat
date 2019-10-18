package torrent;

import org.springframework.stereotype.Service;
import utilities.HttpHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class TorrentSearchEngineService {

    private final List<TorrentSearchEngine> activeSearchEngines = new ArrayList<>();
    private final List<TorrentSearchEngine> allSearchEngines = Arrays.asList(new PirateBay(), new NyaaSi(), new SolidTorrents(), new LeetxTo());

    public void refreshTorrentSearchEngines() {
        final List<TorrentSearchEngine> tempActiveSearchEngines = new ArrayList<>();
        allSearchEngines.parallelStream().forEach(torrentSearchEngine -> {
            if (HttpHelper.isWebsiteResponding(torrentSearchEngine.getBaseUrl(), 10000)) {
                tempActiveSearchEngines.add(torrentSearchEngine);
            }
        });
        activeSearchEngines.clear();
        activeSearchEngines.addAll(tempActiveSearchEngines);
    }

    public List<TorrentSearchEngine> getActiveSearchEngines() {
        return activeSearchEngines;
    }
}

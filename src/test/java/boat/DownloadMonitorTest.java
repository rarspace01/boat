package boat;

import org.springframework.cache.CacheManager;

import boat.info.BluRayComService;
import boat.info.CloudFileService;
import boat.info.CloudService;
import boat.info.QueueService;
import boat.info.TorrentMetaService;
import boat.multifileHoster.MultifileHosterService;
import boat.services.TransferService;
import boat.torrent.TorrentSearchEngineService;
import boat.utilities.HttpHelper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

class DownloadMonitorTest {

    @Mock
    private TorrentSearchEngineService torrentSearchEngineService;
    @Mock
    private TorrentMetaService torrentMetaService;
    @Mock
    private CloudService cloudService;
    @Mock
    private MultifileHosterService multiHosterService;
    @Mock
    private CloudFileService cloudFileService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private QueueService queueService;
    @Mock
    private BluRayComService blurayComService;
    @Mock
    private TransferService transferService;
    @Mock
    private HttpHelper httpHelper;

    private DownloadMonitor downloadMonitor;

    @BeforeEach
    void beforeEach() {
        downloadMonitor = new DownloadMonitor(torrentSearchEngineService, torrentMetaService, cloudService,
            multiHosterService,
            cloudFileService, cacheManager, queueService, blurayComService, transferService, httpHelper);
    }



}
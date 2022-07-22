package boat

import boat.info.BluRayComService
import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.QueueService
import boat.info.TorrentMetaService
import boat.multifileHoster.MultifileHosterService
import boat.services.TransferService
import boat.torrent.TorrentSearchEngineService
import boat.utilities.HttpHelper
import org.mockito.Mock
import org.springframework.cache.CacheManager

internal class DownloadMonitorTest {
    @Mock
    private val torrentSearchEngineService: TorrentSearchEngineService? = null

    @Mock
    private val torrentMetaService: TorrentMetaService? = null

    @Mock
    private val cloudService: CloudService? = null

    @Mock
    private val multiHosterService: MultifileHosterService? = null

    @Mock
    private val cloudFileService: CloudFileService? = null

    @Mock
    private val cacheManager: CacheManager? = null

    @Mock
    private val queueService: QueueService? = null

    @Mock
    private val blurayComService: BluRayComService? = null

    @Mock
    private val transferService: TransferService? = null

    @Mock
    private val httpHelper: HttpHelper? = null
    private val downloadMonitor: DownloadMonitor? = null //    @BeforeEach
    //    void beforeEach() {
    //        downloadMonitor = new DownloadMonitor(torrentSearchEngineService, cloudService,
    //            multiHosterService,
    //            cloudFileService, cacheManager, queueService, blurayComService, transferService, httpHelper);
    //    }
}
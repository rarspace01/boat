package boat

import boat.info.CloudFileService
import boat.info.CloudService
import boat.info.QueueService
import boat.info.TheFilmDataBaseService
import boat.multifileHoster.MultifileHosterService
import boat.services.ConfigurationService
import boat.services.TransferService
import boat.torrent.TorrentSearchEngineService
import boat.utilities.HttpHelper
import io.mockk.mockk

internal class BoatControllerTest {

    private val httpHelper: HttpHelper = mockk()
    private val torrentSearchEngineService: TorrentSearchEngineService = mockk()
    private val cloudService: CloudService = mockk()
    private val theFilmDataBaseService: TheFilmDataBaseService = mockk()
    private val multifileHosterService: MultifileHosterService = mockk()
    private val queueService: QueueService = mockk()
    private val cloudFileService: CloudFileService = mockk()
    private val transferService: TransferService = mockk()
    private val configurationService: ConfigurationService = mockk()

    private val boatController = BoatController(
        httpHelper,
        torrentSearchEngineService,
        cloudService,
        theFilmDataBaseService,
        multifileHosterService,
        queueService,
        cloudFileService,
        transferService,
        configurationService
    )

}
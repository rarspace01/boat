package boat;

import java.util.List;

import org.springframework.cache.CacheManager;

import boat.info.BluRayComService;
import boat.info.CloudFileService;
import boat.info.CloudService;
import boat.info.QueueService;
import boat.info.TorrentMetaService;
import boat.multifileHoster.MultifileHosterService;
import boat.services.TransferService;
import boat.torrent.Torrent;
import boat.torrent.TorrentFile;
import boat.torrent.TorrentSearchEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;

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

    private DownloadMonitor downloadMonitor;

    @BeforeEach
    void beforeEach() {
        downloadMonitor = new DownloadMonitor(torrentSearchEngineService, torrentMetaService, cloudService,
            multiHosterService,
            cloudFileService, cacheManager, queueService, blurayComService, transferService);
    }

    @Test
    void shouldGetETABeforeFirstFile() {
        // Given
        Torrent torrent = new Torrent("Test");
        torrent.lsize = 1000.0;
        TorrentFile torrentFile = new TorrentFile();
        torrentFile.filesize = 1024 * 1024 * 500;
        // When
        final String uploadStatusString = downloadMonitor.getUploadStatusString(torrent, List.of(torrentFile), 0, null);
        // Then
        assertThat(uploadStatusString).doesNotMatch("Uploading: 0/1 done ETA: 00:00:00");
    }

    @Test
    void shouldBuildProperFilenames() {
        // When
        final String filename = downloadMonitor
            .buildFilename("www.url.lol - Movie Title 2018", "www.movie-url.lol-movie_title_2018.mkv");
        // Then
        assertThat(filename).isEqualTo("Movie.Title.2018.mkv");
    }

    @Test
    void shouldBuildProperFilenamesWithoutQuotesAndTorrentInName() {
        // When
        final String filename = downloadMonitor
            .buildFilename("\"www.url.lol - Movie Title 2018.torrent\"", "www.movie-url.lol-movie_title_2018.mkv");
        // Then
        assertThat(filename).isEqualTo("Movie.Title.2018.mkv");
    }

    @Test
    void shouldBuildProperFilenameFromFileIfEmptyName() {
        // When
        final String filename = downloadMonitor
            .buildFilename("", "www.movie-url.lol-movie.title.2018.mkv");
        // Then
        assertThat(filename).isEqualTo("movie.title.2018.mkv");
    }

    @Test
    void shouldBuildProperFilenameFromFileIfNullName() {
        // When
        final String filename = downloadMonitor
            .buildFilename(null, "www.movie-url.lol-movie.title.2018.mkv");
        // Then
        assertThat(filename).isEqualTo("movie.title.2018.mkv");
    }

}
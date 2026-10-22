package boat.torrent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class VideoCodecTest {

    @Test
    fun shouldDetectCodecTags() {
        assertThat(VideoCodec.of("Movie.2024.1080p.WEB.H266-GRP")).isEqualTo(VideoCodec.VVC)
        assertThat(VideoCodec.of("Movie.2024.2160p.WEB-DL.AV1.Opus")).isEqualTo(VideoCodec.AV1)
        assertThat(VideoCodec.of("Movie.2024.1080p.BluRay.x265.10bit")).isEqualTo(VideoCodec.HEVC)
        assertThat(VideoCodec.of("Movie 2024 1080p WEB-DL DDP5 1 H 265-GRP")).isEqualTo(VideoCodec.HEVC)
        assertThat(VideoCodec.of("Movie.2024.HEVC.1080p")).isEqualTo(VideoCodec.HEVC)
        assertThat(VideoCodec.of("Movie.2024.1080p.WEB.VP9")).isEqualTo(VideoCodec.VP9)
        assertThat(VideoCodec.of("Movie.2024.1080p.WEB-DL.H.264-GRP")).isEqualTo(VideoCodec.H264)
        assertThat(VideoCodec.of("Movie (2024) [1080p] x264")).isEqualTo(VideoCodec.H264)
        assertThat(VideoCodec.of("Movie.2004.1080p.BluRay.REMUX.VC-1")).isEqualTo(VideoCodec.VC1)
        assertThat(VideoCodec.of("Movie.2004.DVDRip.XviD-GRP")).isEqualTo(VideoCodec.MPEG4_PART2)
        assertThat(VideoCodec.of("Movie.2004.DivX")).isEqualTo(VideoCodec.MPEG4_PART2)
        assertThat(VideoCodec.of("Movie.2004.PAL.DVDR")).isEqualTo(VideoCodec.MPEG2)
        assertThat(VideoCodec.of("Movie.2004.DVD9.MPEG-2")).isEqualTo(VideoCodec.MPEG2)
        assertThat(VideoCodec.of("Movie.1998.VCD")).isEqualTo(VideoCodec.MPEG1)
    }

    @Test
    fun shouldNotDetectCodecsInsideOtherWords() {
        assertThat(VideoCodec.of("Movie.2024.1080p.WEB")).isNull()
        assertThat(VideoCodec.of("Havc.Avocado.2024")).isNull()
        assertThat(VideoCodec.of("Movie.2024.1080p.DVDRip")).isNull()
    }

    @Test
    fun shouldPreferTheMostEfficientCodecNamed() {
        assertThat(VideoCodec.of("Movie.2024.1080p.x264.to.x265")).isEqualTo(VideoCodec.HEVC)
    }

    @Test
    fun shouldRateCodecsByEfficiency() {
        assertThat(VideoCodec.entries.map { it.rating }).isSortedAccordingTo(Comparator.reverseOrder())
        assertThat(VideoCodec.DEFAULT).isEqualTo(VideoCodec.H264)
    }

    @Test
    fun shouldRankOtherwiseEqualTorrentsByCodec() {
        val names = listOf(
            "Test.Movie.2020.1080p.XviD",
            "Test.Movie.2020.1080p.x264",
            "Test.Movie.2020.1080p.MPEG2",
            "Test.Movie.2020.1080p.AV1",
            "Test.Movie.2020.1080p.x265",
        )
        val torrents = names.map { name ->
            Torrent("Test").apply {
                this.name = name
                sizeInMB = 4000.0
                seeder = 50
                leecher = 10
            }.let { TorrentHelper.evaluateRating(it, "Test Movie") }
        }

        assertThat(torrents.sortedWith(TorrentComparator).map { it.name }).containsExactly(
            "Test.Movie.2020.1080p.AV1",
            "Test.Movie.2020.1080p.x265",
            "Test.Movie.2020.1080p.x264",
            "Test.Movie.2020.1080p.XviD",
            "Test.Movie.2020.1080p.MPEG2",
        )
    }
}

package boat.multifileHoster

import boat.torrent.Torrent
import boat.torrent.TorrentFile
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * How long an upload to the drive still takes and how it is shown. Before the first file is done the duration is
 * estimated from the size (10 MB/s), afterwards from the time the files before took.
 */
object UploadProgress {
    fun getUploadDuration(
            listOfFiles: List<TorrentFile>,
            currentFileNumber: Int,
            startTime: Instant?
    ): Duration {
        val remainingDuration: Duration
        val fileCount = listOfFiles.size
        remainingDuration = if (startTime == null || currentFileNumber == 0) {
            val size = listOfFiles.stream()
                    .map { torrentFile: TorrentFile -> torrentFile.filesize }
                    .reduce(0L) { a: Long, b: Long -> java.lang.Long.sum(a, b) }
            val lsize = size.toDouble() / 1024.0 / 1024.0
            val expectedSecondsRemaining = (lsize / 10.0).toLong()
            Duration.of(expectedSecondsRemaining, ChronoUnit.SECONDS)
        } else {
            val diffTime = Instant.now().toEpochMilli() - startTime.toEpochMilli()
            val milliPerFile = diffTime / currentFileNumber.toLong()
            val remainingFileCount = fileCount - currentFileNumber
            val expectedMilliRemaining = milliPerFile * remainingFileCount
            Duration.of(expectedMilliRemaining, ChronoUnit.MILLIS)
        }
        return remainingDuration
    }

    fun getUploadStatusString(
            torrentToBeDownloaded: Torrent?,
            listOfFiles: List<TorrentFile>,
            currentFileNumber: Int,
            startTime: Instant?
    ): String {
        val remainingDuration = getUploadDuration(listOfFiles, currentFileNumber, startTime)
        return String.format(
                "Uploading: %d/%d done ETA: %02d:%02d:%02d",
                currentFileNumber,
                listOfFiles.size,
                remainingDuration.toHours(),
                remainingDuration.toMinutesPart(),
                remainingDuration.toSecondsPart()
        )
    }
}

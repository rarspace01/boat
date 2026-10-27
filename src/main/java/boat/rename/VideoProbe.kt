package boat.rename

import boat.utilities.LoggerDelegate
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** What is known about a video file: the first video stream and the length of the file. */
data class VideoInfo(
    val codec: String?,
    val width: Int?,
    val height: Int?,
    /** bits per second of the whole file if the stream doesn't tell */
    val bitrate: Long?,
    val durationSeconds: Double?,
)

/** What reading a file gave: the video, a file ffprobe rejects, or no answer which tells something about the file. */
sealed interface ProbeResult {
    data class Read(val info: VideoInfo) : ProbeResult

    /** ffprobe read the file and rejected it (not a video, damaged): it is broken. */
    data class Broken(val reason: String) : ProbeResult

    /** No answer about the file itself: a timeout, an I/O error on the mount, ffprobe not there. Says nothing about the file. */
    data class Failed(val reason: String) : ProbeResult
}

fun interface VideoProbe {
    fun probe(file: File): ProbeResult
}

/**
 * Reads the video metadata with `ffprobe` (part of ffmpeg, it has to be installed). The command gets its arguments
 * directly, there is no shell, so a file name can't break out of it. The files are downloads from strangers: ffprobe
 * may only open local files, a "video" which is a playlist or a concat list can't get it to fetch urls (ffmpeg refuses
 * that by default as well, the whitelist doesn't depend on the build).
 */
class FfprobeVideoProbe(private val command: String = "ffprobe", private val timeoutSeconds: Long = 60) : VideoProbe {

    private val logger by LoggerDelegate()

    override fun probe(file: File): ProbeResult {
        return try {
            val process = ProcessBuilder(
                command, "-v", "error", "-protocol_whitelist", "file", "-select_streams", "v:0",
                "-show_entries", "stream=codec_name,width,height,bit_rate:format=duration,bit_rate,size",
                "-of", "json", "-i", file.absolutePath
            ).start()
            // the answer is read while waiting on threads of its own, a ffprobe which hangs must not hold us longer than the timeout
            val output = CompletableFuture.supplyAsync({ process.inputStream.readAllBytes().toString(Charsets.UTF_8) }, outputReaders)
            val error = CompletableFuture.supplyAsync({ process.errorStream.readAllBytes().toString(Charsets.UTF_8) }, outputReaders)
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                logger.warn("ffprobe took more than {}s for {}", timeoutSeconds, file)
                return ProbeResult.Failed("timeout after ${timeoutSeconds}s")
            }
            classify(process.exitValue(), output.get(5, TimeUnit.SECONDS), error.get(5, TimeUnit.SECONDS))
        } catch (e: Exception) {
            logger.warn("could not run {} for {}: {}", command, file, e.message)
            ProbeResult.Failed("could not run ffprobe: ${e.message}")
        }
    }

    companion object {
        private val outputReaders = Executors.newVirtualThreadPerTaskExecutor()

        /** What ffprobe writes for a file it read and doesn't take, as opposed to one it couldn't read (I/O error, missing). */
        private val rejectedMarkers = listOf("Invalid data found when processing input", "moov atom not found")

        /**
         * Only a file ffprobe really rejects is broken. Any other error (an I/O error of the mount, a missing file) and an
         * answer which can't be parsed say nothing about the file.
         */
        internal fun classify(exitCode: Int, output: String, error: String): ProbeResult {
            val message = error.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()
            if (exitCode != 0) {
                return if (rejectedMarkers.any { error.contains(it) }) ProbeResult.Broken(message)
                else ProbeResult.Failed("ffprobe exited with $exitCode: $message")
            }
            val root = runCatching { JsonParser.parseString(output).asJsonObject }.getOrNull()
                ?: return ProbeResult.Failed("unexpected answer of ffprobe")
            return parse(root)?.let { ProbeResult.Read(it) } ?: ProbeResult.Broken("no video stream")
        }

        /** The answer of `ffprobe -of json`, null if there is no video stream or the answer is not what we expect. */
        internal fun parse(json: String): VideoInfo? =
            runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()?.let { parse(it) }

        private fun parse(root: JsonObject): VideoInfo? {
            val stream = root["streams"]?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return null
            val format = root["format"]?.takeIf { it.isJsonObject }?.asJsonObject
            val duration = format.number("duration")?.takeIf { it > 0 }
            val bitrate = stream.number("bit_rate")?.toLong()
                ?: format.number("bit_rate")?.toLong()
                ?: format.number("size")?.let { size -> duration?.let { (size * 8 / it).toLong() } }
            return VideoInfo(
                codec = stream["codec_name"]?.takeIf { !it.isJsonNull }?.asString,
                width = stream.number("width")?.toInt(),
                height = stream.number("height")?.toInt(),
                bitrate = bitrate,
                durationSeconds = duration,
            )
        }

        // ffprobe writes numbers like bit_rate and duration as strings
        private fun JsonObject?.number(name: String): Double? =
            this?.get(name)?.takeIf { !it.isJsonNull }?.asString?.toDoubleOrNull()
    }
}

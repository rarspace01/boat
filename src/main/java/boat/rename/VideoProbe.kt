package boat.rename

import boat.utilities.LoggerDelegate
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.CompletableFuture
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

fun interface VideoProbe {
    /** Null if the file can't be read. */
    fun probe(file: File): VideoInfo?
}

/**
 * Reads the video metadata with `ffprobe` (part of ffmpeg, it has to be installed). The command gets its arguments
 * directly, there is no shell, so a file name can't break out of it.
 */
class FfprobeVideoProbe(private val command: String = "ffprobe", private val timeoutSeconds: Long = 60) : VideoProbe {

    private val logger by LoggerDelegate()

    override fun probe(file: File): VideoInfo? {
        return try {
            val process = ProcessBuilder(
                command, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=codec_name,width,height,bit_rate:format=duration,bit_rate,size",
                "-of", "json", "-i", file.absolutePath
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            // the answer is read while waiting, a ffprobe which hangs must not hold us longer than the timeout
            val output = CompletableFuture.supplyAsync { process.inputStream.readAllBytes().toString(Charsets.UTF_8) }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                logger.warn("ffprobe took more than {}s for {}", timeoutSeconds, file)
                return null
            }
            if (process.exitValue() != 0) return null
            parse(output.get(5, TimeUnit.SECONDS))
        } catch (e: Exception) {
            logger.warn("could not run {} for {}: {}", command, file, e.message)
            null
        }
    }

    companion object {
        /** The answer of `ffprobe -of json`, null if there is no video stream or the answer is not what we expect. */
        internal fun parse(json: String): VideoInfo? {
            val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
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

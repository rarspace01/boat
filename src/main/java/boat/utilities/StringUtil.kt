package boat.utilities

import java.util.Locale

class StringUtil {

    companion object {
        fun deductFirstTorrentLetter(preparedTorrentName: String): String {
            // replace Umlauts
            var preparedTorrentName = preparedTorrentName
            preparedTorrentName = preparedTorrentName.replace("[äÄ]".toRegex(), "A")
            preparedTorrentName = preparedTorrentName.replace("[öÖ]".toRegex(), "O")
            preparedTorrentName = preparedTorrentName.replace("[üÜ]".toRegex(), "U")

            // take only name infront of year
            val split = preparedTorrentName.split("[1-2][0-9]{3}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (split.size > 0) {
                preparedTorrentName = split[0]
            }
            // remove articles
            preparedTorrentName = preparedTorrentName.replace("^(?:a|an|the|der|die|das)[ .]+".toRegex(RegexOption.IGNORE_CASE), "")
            //
            preparedTorrentName = preparedTorrentName.trim { it <= ' ' }
            preparedTorrentName = preparedTorrentName.replace("[\".]".toRegex(), "")
            if (preparedTorrentName.isNotEmpty()) {
                preparedTorrentName = preparedTorrentName.substring(0, 1)
            }
            preparedTorrentName = preparedTorrentName.replace("[\\W]".toRegex(), "?")
            preparedTorrentName = preparedTorrentName.replace("[0-9]".toRegex(), "0-9")
            preparedTorrentName = preparedTorrentName.uppercase(Locale.getDefault())
            //
            return preparedTorrentName
        }
    }
}
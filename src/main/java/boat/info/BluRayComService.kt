package boat.info

import boat.utilities.HttpHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.util.*
import java.util.regex.Pattern

@Service
class BluRayComService(private val httpHelper: HttpHelper) {

    private val jsonMapper = JsonMapper.builder().build()

    fun getReleasesForMonthAndYear(month: Int, year: Int): List<MediaItem> {
        val listOfMediaItems = arrayListOf<MediaItem>()
        val page = httpHelper.getPage("https://www.blu-ray.com/movies/releasedates.php?year=$year&month=$month")
        val document = Jsoup.parse(page)
        val data = (document.getElementsByAttribute("language")[0].childNode(0) as DataNode).wholeData
        val list = data.split("\n")
        for (entry: String in list) {
            if (entry.matches("movies\\[[0-9]+\\].*".toRegex())) {
                val matcher = Pattern.compile("(\\{.*\\})").matcher(entry)
                while (matcher.find()) {
                    val matchedJSONElement = matcher.group(1)
                    val jsonNode = jsonMapper.readTree(matchedJSONElement)
                    val title = jsonNode.get("title").asString()
                    val year = jsonNode.get("year").asString().toInt()
                    val yearOptional = if (year > 1900) year else null
                    var type = MediaType.Movie
                    if (title.lowercase(Locale.getDefault()).contains("season")) {
                        type = MediaType.Series
                    }
                    listOfMediaItems.add(
                        MediaItem(
                            title = title,
                            originalTitle = title,
                            year = yearOptional,
                            type = type
                        )
                    )
                }
            }
        }
        return listOfMediaItems
    }
}

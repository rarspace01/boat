package pirateboat.info

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.springframework.stereotype.Service
import pirateboat.utilities.HttpHelper
import java.util.regex.Pattern

@Service
class BluRayComService(private val httpHelper: HttpHelper) {

    fun getReleasesForMonthAndYear(month: Int, year: Int): List<MediaItem> {
        val listOfMediaItems = arrayListOf<MediaItem>()
        val page = httpHelper.getPage("https://www.blu-ray.com/movies/releasedates.php?year=${year}&month=${month}")
        val document = Jsoup.parse(page)
        val data = (document.getElementsByAttribute("language")[0].childNode(0) as DataNode).wholeData
        val list = data.split("\n")
        val objectMapper = ObjectMapper()
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
        for (entry: String in list) {
            if (entry.matches("movies\\[[0-9]+\\].*".toRegex())) {
                val matcher = Pattern.compile("(\\{.*\\})").matcher(entry)
                while (matcher.find()) {
                    val matchedJSONElement = matcher.group(1)
                    val jsonNode = objectMapper.readTree(matchedJSONElement)
                    val mediaItem = MediaItem()
                    val title = jsonNode.get("title").asText()
                    mediaItem.title = title
                    mediaItem.originalTitle = title
                    mediaItem.year = jsonNode.get("year").asText().toInt()
                    if (title.toLowerCase().contains("season")) {
                        mediaItem.type = MediaType.Movie
                    } else mediaItem.type = MediaType.Movie
                    listOfMediaItems.add(mediaItem)
                }
            }
        }
        return listOfMediaItems
    }
}
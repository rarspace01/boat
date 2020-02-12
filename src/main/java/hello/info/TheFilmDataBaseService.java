package hello.info;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hello.utilities.HttpHelper;
import hello.utilities.PropertiesHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Service
public class TheFilmDataBaseService {

    private final HttpHelper httpHelper;
    private final String baseUrl = String.format("https://api.themoviedb.org/3/search/multi?api_key=%s",
            PropertiesHelper.getProperty("tfdb_apikey"));

    @Autowired
    public TheFilmDataBaseService(HttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    public List<MediaItem> search(String name) {
        String page = httpHelper.getPage(String.format("%s&query=%s", baseUrl, name));
        return parseResponsePage(page);
    }

    public List<MediaItem> search(String name, int year) {
        String page = httpHelper.getPage(String.format("%s&query=%s&year=%d", baseUrl, name, year));
        return parseResponsePage(page);
    }

    public List<MediaItem> parseResponsePage(String pageContent) {
        List<MediaItem> mediaItems = new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonElement jsonRoot = parser.parse(pageContent);
        JsonElement results = jsonRoot.getAsJsonObject().get("results");
        JsonArray jsonArray = results.getAsJsonArray();
        jsonArray.forEach(jsonMedia -> {
            MediaItem mediaItem = new MediaItem();
            JsonObject jsonMediaObject = jsonMedia.getAsJsonObject();
            String mediaType = jsonMediaObject.get("media_type").getAsString().toLowerCase();
            if (mediaType.contains("tv")) {
                mediaItem.setTitle(jsonMediaObject.get("name").getAsString());
                mediaItem.setOriginalTitle(jsonMediaObject.get("original_name").getAsString());
            } else {
                mediaItem.setTitle(jsonMediaObject.get("title").getAsString());
                mediaItem.setOriginalTitle(jsonMediaObject.get("original_title").getAsString());
            }
            mediaItem.setType(determineMediaType(mediaType));
            try {
                Calendar calendar = Calendar.getInstance();
                JsonElement releaseDate = jsonMediaObject.get("release_date");
                if (releaseDate != null && !(releaseDate instanceof JsonNull)) {
                    calendar.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(releaseDate.getAsString()));
                    mediaItem.setYear(calendar.get(Calendar.YEAR));
                }
            } catch (ParseException ignored) {
            }
            mediaItems.add(mediaItem);
        });
        return mediaItems;
    }

    private MediaType determineMediaType(String mediaTypeString) {
        if (mediaTypeString.toLowerCase().contains("movie")) {
            return MediaType.Movie;
        } else if (mediaTypeString.toLowerCase().contains("series") || mediaTypeString.toLowerCase().contains("tv")) {
            return MediaType.Series;
        } else {
            return MediaType.Other;
        }
    }

}

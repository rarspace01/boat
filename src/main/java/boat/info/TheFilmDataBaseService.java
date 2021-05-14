package boat.info;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import boat.torrent.Torrent;
import boat.torrent.TorrentHelper;
import boat.utilities.HttpHelper;
import boat.utilities.PropertiesHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        String page = httpHelper.getPage(String.format("%s&query=%s", baseUrl, TorrentHelper.urlEncode(name)));
        return parseResponsePage(page);
    }

    public List<MediaItem> search(String name, int year) {
        String urlString = String.format("%s&query=%s&year=%d", baseUrl, TorrentHelper.urlEncode(name), year);
        String page = httpHelper.getPage(urlString);

        return parseResponsePage(page);
    }

    public List<MediaItem> parseResponsePage(String pageContent) {
        if (pageContent == null)
            return null;
        List<MediaItem> mediaItems = new ArrayList<>();
        JsonElement jsonRoot = JsonParser.parseString(pageContent);
        JsonElement results = jsonRoot.getAsJsonObject().get("results");
        JsonArray jsonArray = results.getAsJsonArray();
        jsonArray.forEach(jsonMedia -> {
            JsonObject jsonMediaObject = jsonMedia.getAsJsonObject();
            String mediaTypeString = jsonMediaObject.get("media_type").getAsString().toLowerCase();
            String title = null;
            String originalTitle = null;
            if (mediaTypeString.contains("tv")) {
                title = jsonMediaObject.get("name").getAsString();
                originalTitle = jsonMediaObject.get("original_name").getAsString();
            } else {
                JsonElement titleElement = jsonMediaObject.get("title");
                JsonElement originalTitleElement = jsonMediaObject.get("original_title");
                if (titleElement != null) {
                    title = titleElement.getAsString();
                }
                if (originalTitleElement != null) {
                    originalTitle = originalTitleElement.getAsString();
                }
            }
            MediaType mediaType = determineMediaType(mediaTypeString);
            Integer year = null;
            try {
                Calendar calendar = Calendar.getInstance();
                JsonElement releaseDate = jsonMediaObject.get("release_date");
                JsonElement firstAirDate = jsonMediaObject.get("first_air_date");
                String releaseDateString = releaseDate != null ? releaseDate.getAsString()
                    : (firstAirDate != null ? firstAirDate.getAsString() : null);
                if (releaseDateString != null) {
                    calendar.setTime(new SimpleDateFormat("yyyy-MM-dd").parse(releaseDateString));
                    year = calendar.get(Calendar.YEAR);
                }
            } catch (ParseException ignored) {
            }
            if (title != null || originalTitle != null) {
                mediaItems.add(new MediaItem(title, originalTitle, year, mediaType));
            }
        });
        return mediaItems;
    }

    private MediaType determineMediaType(String mediaTypeString) {
        String mediaTypeStringCleaned = mediaTypeString.toLowerCase();
        if (mediaTypeStringCleaned.contains("movie")) {
            return MediaType.Movie;
        } else if (mediaTypeStringCleaned.contains("series") || mediaTypeStringCleaned.contains("tv")) {
            return MediaType.Series;
        } else {
            return MediaType.Other;
        }
    }

    public MediaType determineMediaType(Torrent remoteTorrent) {
        Integer yearOfRelease = extractYearInTorrent(remoteTorrent.name);
        List<MediaItem> mediaItems = new ArrayList<>();
        if (yearOfRelease != null) {
            mediaItems.addAll(search(TorrentHelper.getNormalizedTorrentStringWithSpaces(remoteTorrent.name).replaceAll(yearOfRelease.toString(), "").trim(), yearOfRelease));
        } else {
            mediaItems.addAll(search(TorrentHelper.getNormalizedTorrentStringWithSpaces(remoteTorrent.name).trim()));
        }
        Optional<MediaItem> mediaItem = mediaItems.stream().findFirst();
        return mediaItem.map(MediaItem::getType).orElse(null);
    }

    private Integer extractYearInTorrent(String torrentName) {
        Pattern pattern = Pattern.compile("([0-9]{4})[^\\w]");
        Matcher matcher = pattern.matcher(torrentName);
        while (matcher.find()) {
            // Get the group matched using group() method
            String group = matcher.group(1);
            if (group != null)
                return Integer.parseInt(group);
        }
        return null;
    }

}

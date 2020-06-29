package pirateboat.info;

import org.springframework.stereotype.Service;
import pirateboat.torrent.Torrent;
import pirateboat.torrent.TorrentHelper;
import pirateboat.torrent.TorrentService;
import pirateboat.utilities.PropertiesHelper;

@Service
public class CloudService {

    final TorrentService torrentService = new TorrentService();

    public String buildDestinationPath(final Torrent torrent) {
        String basePath = PropertiesHelper.getProperty("rclonedir");
        String normalizedTorrentStringWithSpaces = TorrentHelper.getNormalizedTorrentStringWithSpacesKeepCase(torrent.name);
        String cleanedString = removeReleaseTags(normalizedTorrentStringWithSpaces);
        // matches movie pattern
        final String typeOfMedia = determineTypeOfMedia(cleanedString);
        // take only name infront of year
        String[] split = cleanedString.split("[1-2][0-9]{3}");
        if (split.length > 0) {
            cleanedString = split[0];
        }
        // remove articles
        cleanedString = cleanedString.replaceAll("(A[ .]|The[ .]|Der[ .])", "");
        //
        cleanedString = cleanedString.trim();
        if (cleanedString.length() > 0) {
            cleanedString = cleanedString.substring(0, 1);
        }
        cleanedString=cleanedString.toUpperCase();
        //
        return basePath + "/" + typeOfMedia + "/" + cleanedString + "/";
    }

    private String determineTypeOfMedia(String cleanedString) {
        if (cleanedString.matches("(.+[ .]+S[0-9]+.+)|(.+Season.+)")) {
            return "Series-Shows";
        } else if (isMovieString(cleanedString)) {
            return "Movies";
        }
        return "transfer";
    }

    private boolean isMovieString(String string) {
        return string.matches(".+[1-2][0-9]{3}.*");
    }

    private String removeReleaseTags(final String string) {
        StringBuilder releaseTagsRemoved = new StringBuilder(string);
        torrentService.getReleaseTags().forEach(tag -> {
            String temporaryString = releaseTagsRemoved.toString().replaceAll("\\s(?i)" + tag + "\\s", " ");
            releaseTagsRemoved.setLength(0);
            releaseTagsRemoved.append(temporaryString);
        });
        return releaseTagsRemoved.toString();
    }

}

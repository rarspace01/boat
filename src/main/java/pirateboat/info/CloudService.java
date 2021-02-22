package pirateboat.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import pirateboat.torrent.TorrentHelper;
import pirateboat.torrent.TorrentService;
import pirateboat.utilities.PropertiesHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static pirateboat.torrent.TorrentHelper.MOVIES;
import static pirateboat.torrent.TorrentHelper.SERIES_SHOWS;
import static pirateboat.torrent.TorrentHelper.TRANSFER;

@Log4j2
@Service
public class CloudService {

    final TorrentService torrentService = new TorrentService();

    public String buildDestinationPath(final String torrentName) {
        String basePath = PropertiesHelper.getProperty("rclonedir");
        String preparedTorrentName = TorrentHelper.prepareTorrentName(torrentName);
        final String typeOfMedia = TorrentHelper.determineTypeOfMedia(preparedTorrentName);
        preparedTorrentName = deductFirstTorrentLetter(preparedTorrentName);
        return basePath + "/" + typeOfMedia + "/" + preparedTorrentName + "/";
    }

    public String buildDestinationPathWithTypeOfMedia(final String torrentName, String typeOfMedia) {
        String basePath = PropertiesHelper.getProperty("rclonedir");
        String preparedTorrentName = TorrentHelper.prepareTorrentName(torrentName);
        preparedTorrentName = deductFirstTorrentLetter(preparedTorrentName);
        return basePath + "/" + typeOfMedia + "/" + preparedTorrentName + "/";
    }

    @NotNull
    private String deductFirstTorrentLetter(String preparedTorrentName) {
        // take only name infront of year
        String[] split = preparedTorrentName.split("[1-2][0-9]{3}");
        if (split.length > 0) {
            preparedTorrentName = split[0];
        }
        // remove articles
        preparedTorrentName = preparedTorrentName.replaceAll("(A[ .]|The[ .]|Der[ .])", "");
        //
        preparedTorrentName = preparedTorrentName.trim();
        preparedTorrentName = preparedTorrentName.replaceAll("\"", "");
        if (preparedTorrentName.length() > 0) {
            preparedTorrentName = preparedTorrentName.substring(0, 1);
        }
        preparedTorrentName = preparedTorrentName.toUpperCase();
        //
        return preparedTorrentName;
    }

    public List<String> findExistingFiles(String searchName) {
        final String[] strings = searchName.split(" ");
        final List<String> foundFilesMovies = findFilesBasedOnStringsAndMediaType(searchName, strings, MOVIES);
        if (!foundFilesMovies.isEmpty()) return foundFilesMovies;
        // check series
        final List<String> foundFilesSeries = findFilesBasedOnStringsAndMediaType(searchName, strings, SERIES_SHOWS);
        if (!foundFilesSeries.isEmpty()) return foundFilesSeries;
        // check transfer
        return findFilesBasedOnStringsAndMediaType(searchName, strings, TRANSFER);
    }

    private List<String> findFilesBasedOnStringsAndMediaType(String searchName, String[] strings, String movies) {
        log.info("Searching for: {}", searchName);
        return getFilesInPath(buildDestinationPathWithTypeOfMedia(searchName, movies)).stream()
                .filter(fileName -> Arrays.stream(strings).allMatch(searchStringPart -> fileName.toLowerCase().matches(".*" + searchStringPart.toLowerCase() + ".*")))
                .collect(Collectors.toList());
    }

    List<String> getFilesInPath(String destinationPath) {
        final List<String> fileList = new ArrayList<>();
        final long startCounter = System.currentTimeMillis();
        log.info("Search in [" + destinationPath + "]");
        ProcessBuilder builder = new ProcessBuilder();
        final String commandToRun = String.format("rclone lsjson '%s'", destinationPath);
        log.info(commandToRun);
        builder.command("bash", "-c", commandToRun);
        builder.directory(new File(System.getProperty("user.home")));
        Process process = null;
        int exitCode = -1;
        try {
            process = builder.start();
            exitCode = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes());
            final JsonElement jsonElement = JsonParser.parseString(output);
            if (jsonElement.isJsonArray()) {
                jsonElement.getAsJsonArray()
                        .forEach(jsonElement1 -> {
                            fileList.add(destinationPath + jsonElement1.getAsJsonObject().get("Path").getAsString());
                        });
            } else {
                fileList.add(destinationPath + jsonElement.getAsJsonObject().get("Path").getAsString());
            }
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        assert exitCode == 0;
        log.info("Took {}ms", System.currentTimeMillis() - startCounter);
        return fileList;
    }

}

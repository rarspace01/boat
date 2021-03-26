package pirateboat.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import pirateboat.torrent.TorrentHelper;
import pirateboat.torrent.TorrentType;
import pirateboat.utilities.PropertiesHelper;

import java.io.File;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Log4j2
@Service
public class CloudService {

    public String buildDestinationPath(final String torrentName) {
        String basePath = PropertiesHelper.getProperty("rclonedir");
        String preparedTorrentName = TorrentHelper.prepareTorrentName(torrentName);
        final TorrentType typeOfMedia = TorrentHelper.determineTypeOfMedia(preparedTorrentName);
        preparedTorrentName = deductFirstTorrentLetter(preparedTorrentName);
        return basePath + "/" + typeOfMedia.getType() + "/" + preparedTorrentName + "/";
    }

    public String buildDestinationPathWithTypeOfMedia(final String torrentName, TorrentType typeOfMedia) {
        String basePath = PropertiesHelper.getProperty("rclonedir");
        String preparedTorrentName = TorrentHelper.prepareTorrentName(torrentName);
        preparedTorrentName = deductFirstTorrentLetter(preparedTorrentName);
        return basePath + "/" + typeOfMedia.getType() + "/" + preparedTorrentName + "/";
    }

    @NotNull
    private String deductFirstTorrentLetter(String preparedTorrentName) {
        // take only name infront of year
        String[] split = preparedTorrentName.split("[1-2][0-9]{3}");
        if (split.length > 0) {
            preparedTorrentName = split[0];
        }
        // remove articles
        preparedTorrentName = preparedTorrentName.replaceAll("(a[ .]|the[ .]|der[ .])", "");
        //
        preparedTorrentName = preparedTorrentName.trim();
        preparedTorrentName = preparedTorrentName.replaceAll("\"", "");
        if (preparedTorrentName.length() > 0) {
            preparedTorrentName = preparedTorrentName.substring(0, 1);
        }
        preparedTorrentName = preparedTorrentName.replaceAll("[0-9]","0-9");
        preparedTorrentName = preparedTorrentName.toUpperCase();
        //
        return preparedTorrentName;
    }

    public List<String> findExistingFiles(String searchName) {
        final String[] strings = searchName.split(" ");

        ForkJoinPool customThreadPool = new ForkJoinPool(TorrentType.values().length);
        try {
            return customThreadPool.submit(() -> Arrays.asList(TorrentType.values()).parallelStream()
                    .map(torrentType -> findFilesBasedOnStringsAndMediaType(searchName, strings, torrentType))
                    .flatMap(List::stream)
                    .collect(Collectors.toList())
            ).get();
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }


    private List<String> findFilesBasedOnStringsAndMediaType(String searchName, String[] strings, TorrentType torrentType) {
        log.info("Searching for: {}", searchName);
        return getFilesInPath(buildDestinationPathWithTypeOfMedia(searchName, torrentType)).stream()
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
        boolean exitCode = true;
        try {
            process = builder.start();
            exitCode = process.waitFor(10, TimeUnit.SECONDS);
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
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        log.info("Took {}ms with [{}]", System.currentTimeMillis() - startCounter, destinationPath);
        return fileList;
    }

}

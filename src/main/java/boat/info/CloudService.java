package boat.info;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import boat.torrent.TorrentHelper;
import boat.torrent.TorrentType;
import boat.utilities.PropertiesHelper;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
@Service
public class CloudService {

    private final CloudFileService cloudFileService;

    CloudService(CloudFileService cloudFileService) {
        this.cloudFileService = cloudFileService;
    }

    public boolean isCloudTokenValid() {
        return cloudFileService.getFilesInPath(PropertiesHelper.getProperty("rclonedir")).size() > 0;
    }

    public String buildDestinationPath(final String torrentName) {
        String basePath = PropertiesHelper.getProperty("rclonedir");
        String preparedTorrentName = TorrentHelper.prepareTorrentName(torrentName);
        final TorrentType typeOfMedia = TorrentHelper.determineTypeOfMedia(torrentName);
        String torrentNameFirstLetterDeducted = deductFirstTorrentLetter(preparedTorrentName);
        String optionalSeriesString = "";
        if (TorrentType.SERIES_SHOWS.equals(typeOfMedia)) {
            optionalSeriesString = deductSeriesNameFrom(preparedTorrentName) + "/";
        }
        return basePath + "/" + typeOfMedia.getType() + "/" + torrentNameFirstLetterDeducted + "/"
            + optionalSeriesString + "/";
    }

    private String deductSeriesNameFrom(String preparedTorrentName) {
        return Arrays.stream(preparedTorrentName
            .toLowerCase()
            .trim()
            .replaceAll("s[0-9]+e[0-9]+", "")
            .replaceAll("season[.\\s]?[0-9-]+", "")
            .trim()
            .replaceAll("\\s+", ".")
            .split("\\."))
            .map(StringUtils::capitalize)
            .collect(Collectors.joining("."));
    }

    public String buildDestinationPathWithTypeOfMedia(final String torrentName, TorrentType typeOfMedia) {
        String basePath = PropertiesHelper.getProperty("rclonedir");
        String preparedTorrentName = TorrentHelper.prepareTorrentName(torrentName);
        String firstTorrentLetter = deductFirstTorrentLetter(preparedTorrentName);
        return basePath + "/" + typeOfMedia + "/" + firstTorrentLetter + "/";
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
        preparedTorrentName = preparedTorrentName.replaceAll("[0-9]", "0-9");
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


    private List<String> findFilesBasedOnStringsAndMediaType(String searchName, String[] strings,
                                                             TorrentType torrentType) {
        final String destinationPath = buildDestinationPathWithTypeOfMedia(searchName, torrentType);
        log.info("Searching for: {} with {} in {}", searchName, torrentType.getType(), destinationPath);
        return cloudFileService.getFilesInPath(destinationPath).stream()
            .filter(fileName -> Arrays.stream(strings).allMatch(
                searchStringPart -> fileName.toLowerCase().matches(".*" + searchStringPart.toLowerCase() + ".*")))
            .collect(Collectors.toList());
    }


}

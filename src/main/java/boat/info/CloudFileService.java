package boat.info;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CloudFileService {

    @Getter
    @Setter
    private boolean isCacheFilled = false;

    @Cacheable("filesCache")
    public List<String> getFilesInPath(String destinationPath) {
        final List<String> fileList = new ArrayList<>();
        final long startCounter = System.currentTimeMillis();
        log.debug("Search in [" + destinationPath + "]");
        ProcessBuilder builder = new ProcessBuilder();
        final String commandToRun = String.format("rclone lsjson '%s'", destinationPath);
        log.debug(commandToRun);
        builder.command("bash", "-c", commandToRun);
        builder.directory(new File(System.getProperty("user.home")));
        try {
            Process process = builder.start();
            process.waitFor(10, TimeUnit.SECONDS);
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

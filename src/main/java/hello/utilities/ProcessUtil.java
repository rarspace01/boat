package hello.utilities;

import java.io.File;
import java.io.IOException;

public class ProcessUtil {

    public static boolean isRcloneInstalled() {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command("bash","-c","rlcone", "version");
        builder.directory(new File(System.getProperty("user.home")));
        Process process;
        try {
            process = builder.start();
            process.waitFor();
            final String output = new String(process.getInputStream().readAllBytes());
            return output.contains("rclone");
        } catch (final IOException | InterruptedException exception) {
            exception.printStackTrace();
        }
        return false;
    }
}

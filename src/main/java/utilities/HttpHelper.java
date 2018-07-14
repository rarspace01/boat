package utilities;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

/**
 * Created by denis on 02/10/2016.
 */
public class HttpHelper {
    private static String charset = "UTF-8";  // Or in Java 7 and later, use the constant: java.nio.charset.StandardCharsets.UTF_8.name()

    public static String getPage(String url, List<String> params, String cookies) {
        String returnString;
        StringBuilder buildString = new StringBuilder();

        URLConnection connection;
        try {
            connection = new URL(url).openConnection();
            connection.setRequestProperty("User-Agent", "");
            connection.setRequestProperty("Accept-Charset", charset);

            if (cookies != null) {
                connection.setRequestProperty("Cookie", cookies);
            }

            InputStream response = connection.getInputStream();

//            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
//                System.out.println(header.getKey() + "=" + header.getValue());
//            }

            String contentType = connection.getHeaderField("Content-Type");
            String charset = null;

            for (String param : contentType.replace(" ", "").split(";")) {
                if (param.startsWith("charset=")) {
                    charset = param.split("=", 2)[1];
                    break;
                }
            }

            if (charset == null) {
                charset = "UTF-8";
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response, charset))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    buildString.append(line).append(System.getProperty("line.separator"));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        returnString = buildString.toString();

        return returnString;
    }

    public static String getPage(String url) {

        return getPage(url, null, null);

    }

    public static String getPage(String url, List<String> params) {

        return getPage(url, params, null);

    }

    public static void downloadFileToPath(String fileURLFromTorrent, String localPath) throws IOException {
        URL remoteUrl = new URL(fileURLFromTorrent);
        ReadableByteChannel rbc = Channels.newChannel(remoteUrl.openStream());
        FileOutputStream fos = new FileOutputStream(localPath);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    }

}

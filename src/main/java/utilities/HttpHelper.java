package utilities;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class HttpHelper {
    private static String charset = "UTF-8";  // Or in Java 7 and later, use the constant: java.nio.charset.StandardCharsets.UTF_8.name()
    private static TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
    };


    public static String getPage(String url, List<String> params, String cookies) {
        String returnString;
        StringBuilder buildString = new StringBuilder();

        URLConnection connection;
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, null);

            connection = new URL(url).openConnection();
            ((HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
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
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
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

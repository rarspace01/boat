package boat.utilities;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.stereotype.Service;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class HttpHelper {

    private static String charset = "UTF-8";  // Or in Java 7 and later, use the constant: java.nio.charset.StandardCharsets.UTF_8.name()
    private static final Logger log = LoggerFactory.getLogger(HttpHelper.class);
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

    public String getPage(String url, List<String> params, String cookies) {
        return getPage(url, params, cookies, 30 * 1000);
    }

    public String getPageWithShortTimeout(String url, List<String> params, String cookies) {
        return getPage(url, params, cookies, 10 * 1000);
    }

    public String getPage(String url, List<String> params, String cookies, int timeout) {
        String returnString;
        StringBuilder buildString = new StringBuilder();

        URLConnection connection;
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, null);

            connection = new URL(url).openConnection();
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
            }
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:68.0) Gecko/20100101 Firefox/68.0");
            connection.setRequestProperty("Accept-Charset", charset);
            connection.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            if (cookies != null) {
                connection.setRequestProperty("Cookie", cookies);
            }

            InputStream response = connection.getInputStream();

//            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
//                System.out.println(header.getKey() + "=" + header.getValue());
//            }

            String contentType = connection.getHeaderField("Content-Type");
            String charset = null;

            if (contentType != null) {
                for (String param : contentType.replace(" ", "").split(";")) {
                    if (param.startsWith("charset=")) {
                        charset = param.split("=", 2)[1];
                        break;
                    }
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

        } catch (IOException | NoSuchAlgorithmException | KeyManagementException ignored) {
        }

        returnString = buildString.toString();

        return returnString;
    }

    public byte[] getRawPage(String url, List<String> params, String cookies, int timeout) {

        URLConnection connection;
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, null);

            connection = new URL(url).openConnection();
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sc.getSocketFactory());
            }
            connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:68.0) Gecko/20100101 Firefox/68.0");
            connection.setRequestProperty("Accept-Charset", charset);
            connection.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            if (cookies != null) {
                connection.setRequestProperty("Cookie", cookies);
            }

            InputStream response = connection.getInputStream();

//            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
//                System.out.println(header.getKey() + "=" + header.getValue());
//            }

            String contentType = connection.getHeaderField("Content-Type");
            String charset = null;

            if (contentType != null) {
                for (String param : contentType.replace(" ", "").split(";")) {
                    if (param.startsWith("charset=")) {
                        charset = param.split("=", 2)[1];
                        break;
                    }
                }
            }

            if (charset == null) {
                charset = "UTF-8";
            }

            return IOUtils.toByteArray(response);

        } catch (IOException | NoSuchAlgorithmException | KeyManagementException ignored) {
        }

        return null;
    }

    public String getPage(String url) {
        return getPage(url, null, null);
    }

    public String getPageWithShortTimeout(String url) {
        return getPage(url, null, null, 10 * 1000);
    }

    public String getPage(String url, int timeout) {
        return getPage(url, null, null, timeout);
    }

    public String getPage(String url, List<String> params) {
        return getPage(url, params, null);
    }

    public static void downloadFileToPath(String fileURLFromTorrent, String localPath) throws IOException {
        URL remoteUrl = new URL(fileURLFromTorrent);
        ReadableByteChannel rbc = Channels.newChannel(remoteUrl.openStream());
        FileOutputStream fos = new FileOutputStream(localPath);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    }

    public boolean isWebsiteResponding(String baseUrl, int timeout) {
        return getPage(baseUrl, timeout).length() > 0;
    }

    public String getExternalHostname() {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("bash", "-c", "dig -x $(curl -s checkip.amazonaws.com) +short");
        builder.directory(new File(System.getProperty("user.home")));
        String output = "";
        String error = "";
        try {
            Process process = builder.start();
            process.waitFor(5, TimeUnit.SECONDS);
            output = new String(process.getInputStream().readAllBytes());
        } catch (Exception e) {
            log.error("{}\nOutput from process:\n{}\nError from Process:\n{}", e.getMessage(), output, error);
            e.printStackTrace();
        }
        return output;
    }
}

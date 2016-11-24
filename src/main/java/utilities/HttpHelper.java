package utilities;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import torrent.Torrent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.sun.org.apache.xalan.internal.xsltc.compiler.util.Type.Int;

/**
 * Created by deha on 02/10/2016.
 */
public class HttpHelper {
    private static String charset = "UTF-8";  // Or in Java 7 and later, use the constant: java.nio.charset.StandardCharsets.UTF_8.name()

    public static String getPage(String url, List<String> params, String cookies){
        String returnString = null;
        StringBuilder buildString = new StringBuilder();

        URLConnection connection = null;
        try {
            connection = new URL(url).openConnection();
            connection.setRequestProperty("User-Agent","");
            connection.setRequestProperty("Accept-Charset", charset);

            if(cookies != null) {
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

            if (charset != null) {
                returnString = "";
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response, charset))) {
                    for (String line; (line = reader.readLine()) != null;) {
                        //System.out.println(line);
                        buildString.append(line+System.getProperty("line.separator"));
                    }
                }
            }
            else {
                // It's likely binary content, use InputStream/OutputStream.
                System.out.print("binary response");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        returnString = buildString.toString();

        return returnString;
    }

    public static String getPage(String url){

        return getPage(url, null,null);

    }

    public static String getPage(String url, List<String> params) {

        return getPage(url, params, null);

    }


}

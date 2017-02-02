package utilities;

import torrent.Torrent;

import java.io.*;
import java.util.HashMap;
import java.util.Properties;

/**
 * Created by denis on 24/11/2016.
 */
public class PropertiesHelper {


    public static final String TORRENTCACHE = "torrentboat.torrents";
    public static final String PROPERTIY_FILE = "torrentboat.cfg";
    private HashMap<String, String> torrentStates = new HashMap<>();

    public static String getProperty(String propname) {
        InputStream inputStream = null;

        String result = null;

        try {

            Properties prop = new Properties();
            inputStream = new FileInputStream(PROPERTIY_FILE);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + PROPERTIY_FILE + "' not found in the classpath");
            }

            result = prop.getProperty(propname);
            inputStream.close();

        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }

        return result;
    }

    public String getState(Torrent torrent) {
        String result = null;

        try {
            InputStream inputStream = null;
            Properties prop = new Properties();
            inputStream = new FileInputStream(TORRENTCACHE);

            if (inputStream != null) {
                prop.load(inputStream);
            }

            result = prop.getProperty(torrent.remoteId);
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;

    }

    public static void writeState(Torrent torrent) {
        try {
            InputStream inputStream = null;

            Properties properties = new Properties();

            inputStream = new FileInputStream(TORRENTCACHE);

            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + TORRENTCACHE + "' not found in the classpath");
            }

            inputStream.close();

            properties.setProperty(torrent.remoteId, torrent.status);

            File file = new File(TORRENTCACHE);
            FileOutputStream fileOut = new FileOutputStream(file);
            properties.store(fileOut, "Favorite Things");
            fileOut.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}

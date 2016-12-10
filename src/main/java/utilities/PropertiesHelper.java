package utilities;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by denis on 24/11/2016.
 */
public class PropertiesHelper {


    public static String getProperty(String propname) {
        InputStream inputStream = null;

        String result = null;

        try {

        Properties prop = new Properties();
        String propFileName = "torrentboat.cfg";
        inputStream = new FileInputStream(propFileName);

        //inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

        if (inputStream != null) {
            prop.load(inputStream);
        } else {
            throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
        }

        result = prop.getProperty(propname);
        inputStream.close();

        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }

        return result;
    }

}

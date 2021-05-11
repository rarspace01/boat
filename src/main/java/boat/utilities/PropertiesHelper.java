package boat.utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesHelper {


    public static final String PROPERTIY_FILE = "boat.cfg";
    public static final String PROPERTIY_FILE_DEFAULT = "boat.default.cfg";
    public static final String VERSION_FILE = "version.properties";

    public static String getVersion() {
        InputStream inputStream;
        String result = null;

        try {

            Properties prop = new Properties();

            inputStream = PropertiesHelper.class.getClassLoader().getResourceAsStream(VERSION_FILE);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + VERSION_FILE + "' not found in the classpath");
            }

            result = prop.getProperty("version");
            inputStream.close();

        } catch (Exception e) {
            System.out.println("Exception: " + e);
        }

        return result;
    }

    public static String getProperty(String propname) {
        InputStream inputStream;
        String result = null;

        // check if property exists in Environment, then skip
        String envValue = System.getenv(propname);
        if(envValue != null){
            return envValue;
        }

        if (getPropertyFile() != null) {
            try {

                Properties prop = new Properties();
                inputStream = new FileInputStream(getPropertyFile());

                if (inputStream != null) {
                    prop.load(inputStream);
                } else {
                    throw new FileNotFoundException("property file '" + getPropertyFile() + "' not found in the classpath");
                }

                result = prop.getProperty(propname);
                inputStream.close();

            } catch (Exception e) {
                System.out.println("Exception: " + e);
            }
        }
        return result;
    }

    private static String getPropertyFile() {
        if (new File(PROPERTIY_FILE).isFile()) {
            return PROPERTIY_FILE;
        } else if (new File(PROPERTIY_FILE_DEFAULT).isFile()) {
            return PROPERTIY_FILE_DEFAULT;
        }
        return null;
    }

}

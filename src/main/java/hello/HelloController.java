package hello;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class HelloController {

    @RequestMapping("/")
    public String index() {
        return "Greetings from Spring Boot!";
    }

/*    @RequestMapping("/boat")
    public String getTorrents(Map<String, Object> model) {
        PirateBay pirateBay = new PirateBay();


        List<Torrent> resultList = pirateBay.searchTorrents("trainspotting");

        System.out.println("G: " + resultList.size());

        return "";
    }*/

}

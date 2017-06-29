package hello;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import torrent.PirateBay;
import torrent.Torrent;

import java.util.List;
import java.util.Map;

@RestController
public class HelloController {

    @RequestMapping("/")
    public String index() {
        return "Greetings from Spring Boot!";
    }

    @RequestMapping("/boat")
    public String getTorrents(Map<String, Object> model) {
        PirateBay pirateBay = new PirateBay();
        List<Torrent> resultList = pirateBay.searchTorrents("trainspotting");
        return "G: " + resultList;
    }

}

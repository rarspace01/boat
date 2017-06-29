package hello

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import torrent.PirateBay
import torrent.Torrent

@RestController
class HelloController {

    @RequestMapping("/")
    fun index(): String {
        return "Greetings from Spring Boot!"
    }

    @RequestMapping("/boat")
    fun getTorrents(@RequestParam("q") searchString: String): String {
        val pirateBay = PirateBay()
        val resultList = pirateBay.searchTorrents(searchString)
        return "G: " + resultList.subList(0, Math.min(resultList.size, 3))
    }

}
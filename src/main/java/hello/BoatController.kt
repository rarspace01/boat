package hello

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import torrent.PirateBay
import torrent.Premiumize
import torrent.Torrent
import java.util.*

@RestController
class BoatController {

    @RequestMapping("/")
    fun index(): String {
        return "Greetings from Spring Boot!"
    }

    @RequestMapping("/boat")
    fun getTorrents(@RequestParam("q") searchString: String): String {
        val pirateBay = PirateBay()
        val resultList = pirateBay.searchTorrents(searchString)
        return "G: " + resultList.subList(0, Math.min(resultList.size, 10))
    }

    @RequestMapping("/boat/download")
    fun downloadTorrent(@RequestParam("d") downloadUri: String): String {
        val decodedUri = String(Base64.getUrlDecoder().decode(downloadUri))
        val torrentToBeDownloaded = Torrent()
        torrentToBeDownloaded.magnetUri = decodedUri.toString()
        return Premiumize().addTorrentToQueue(torrentToBeDownloaded)
    }

}
package boat.multifileHoster

import boat.model.TransferStatus
import boat.torrent.Torrent
import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

/** The answers are those of the AllDebrid API with its demo key (docs.alldebrid.com). */
internal class AlldebridTest {

    private val httpHelper: HttpHelper = mockk()
    private val alldebrid = Alldebrid(httpHelper, apiKey = { "secret-key" }, delayedLinkPollMillis = 1, delayedLinkMaxPolls = 3)

    private fun answer(endpoint: String, json: String) {
        every { httpHelper.getPage("https://api.alldebrid.com/$endpoint", any<String>(), any()) } returns json
    }

    private fun answer(endpoint: String, body: String, json: String) {
        every { httpHelper.getPage("https://api.alldebrid.com/$endpoint", body, any()) } returns json
    }

    private val statusAnswer = """{"status":"success","data":{"magnets":[
        {"id":123456,"filename":"ubuntu-16.04.2-live-server-amd64.iso","hash":"123456789cef825718eda30637230585e3330599","size":587400285,
         "status":"Downloading","statusCode":1,"downloaded":255400192,"uploaded":0,"seeders":7,"downloadSpeed":18874368,"uploadSpeed":0,"uploadDate":1557133868,"completionDate":0},
        {"id":56789,"filename":"ubuntu-20.04.2-live-server-amd64.iso","hash":"987654321cef825718eda30637230585e3330599","size":256400192,
         "status":"Ready","statusCode":4,"uploadDate":1657133868,"completionDate":1657133968,"nbLinks":1},
        {"id":99999,"filename":"ubuntu-12.04.2-live-server-amd64.iso","hash":"999999999cef825718eda30637230585e3330599","size":256400192,
         "status":"Error : Download took more than 72h","statusCode":10,"uploadDate":1657133868},
        {"filename":"broken, no id"}]}}"""

    @Test
    fun shouldSendTheApiKeyInTheHeaderNotInTheUrl() {
        val url = slot<String>()
        val headers = slot<Map<String, String>>()
        every { httpHelper.getPage(capture(url), any<String>(), capture(headers)) } returns statusAnswer

        alldebrid.getRemoteTorrents()

        assertThat(url.captured).isEqualTo("https://api.alldebrid.com/v4.1/magnet/status").doesNotContain("secret-key")
        assertThat(headers.captured).containsEntry("Authorization", "Bearer secret-key")
            .containsEntry("Content-Type", "application/x-www-form-urlencoded")
    }

    @Test
    fun shouldReadTheMagnetsOfTheStatus() {
        answer("v4.1/magnet/status", statusAnswer)

        val torrents = alldebrid.getRemoteTorrents()

        // the broken one is skipped, not all of them
        assertThat(torrents).hasSize(3)
        val downloading = torrents[0]
        assertThat(downloading.remoteId).isEqualTo("123456")
        assertThat(downloading.source).isEqualTo("Alldebrid")
        assertThat(downloading.name).isEqualTo("ubuntu-16.04.2-live-server-amd64.iso")
        assertThat(downloading.remoteTransferStatus).isEqualTo(TransferStatus.DOWNLOADING_TO_MULTIHOSTER)
        assertThat(downloading.remoteProgressInPercent).isBetween(0.43, 0.44)
        assertThat(downloading.eta).isEqualTo(Duration.ofSeconds((587400285L - 255400192L) / 18874368L))
        assertThat(downloading.torrentId).isEqualTo("123456789cef825718eda30637230585e3330599")
        assertThat(downloading.sizeInMB).isBetween(560.0, 561.0)
        val ready = torrents[1]
        assertThat(ready.remoteTransferStatus).isEqualTo(TransferStatus.READY_TO_BE_DOWNLOADED)
        assertThat(ready.remoteProgressInPercent).isEqualTo(1.0)
        assertThat(ready.eta).isEqualTo(Duration.ZERO)
        assertThat(torrents[2].remoteTransferStatus).isEqualTo(TransferStatus.ERROR)
    }

    @Test
    fun shouldReturnTheIdOfAnUploadedMagnet() {
        val magnet = "magnet:?xt=urn:btih:10e93389d579ba4538c68b8d297268a81620fae2&dn=test"
        val body = slot<String>()
        every { httpHelper.getPage("https://api.alldebrid.com/v4/magnet/upload", capture(body), any()) } returns
                """{"status":"success","data":{"magnets":[{"magnet":"$magnet","hash":"10e93389d579ba4538c68b8d297268a81620fae2","name":"test","size":48216647,"id":123456,"ready":false}]}}"""

        val result = alldebrid.addTorrentToDownloadQueue(Torrent.of(magnet))

        assertThat(result.remoteId).isEqualTo("123456")
        assertThat(result.message).doesNotContain("error")
        // the magnet is one encoded form value, its own parameters (dn, tr) stay inside
        assertThat(body.captured).isEqualTo("magnets%5B%5D=magnet%3A%3Fxt%3Durn%3Abtih%3A10e93389d579ba4538c68b8d297268a81620fae2%26dn%3Dtest")
    }

    @Test
    fun shouldReportAFailedUploadAsError() {
        answer("v4/magnet/upload", """{"status":"success","data":{"magnets":[{"magnet":"x","error":{"code":"MAGNET_INVALID_URI","message":"Magnet is not valid."}}]}}""")
        assertThat(alldebrid.addTorrentToDownloadQueue(Torrent.of("x")).let { it.remoteId to it.message.contains("error") }).isEqualTo(null to true)

        answer("v4/magnet/upload", """{"status":"error","error":{"code":"AUTH_BAD_APIKEY","message":"The auth apikey is invalid"}}""")
        assertThat(alldebrid.addTorrentToDownloadQueue(Torrent.of("x")).message).contains("error")

        answer("v4/magnet/upload", "")
        assertThat(alldebrid.addTorrentToDownloadQueue(Torrent.of("x")).message).contains("error")
    }

    private val filesAnswer = """{"status":"success","data":{"magnets":[{"id":56789,"files":[
        {"n":"ubuntu-18.04.2-live-server-amd64.iso","s":145517304,"l":"https:\/\/alldebrid.com\/f\/iso"},
        {"n":"docs","e":[{"n":"README.txt","s":87207,"l":"https:\/\/alldebrid.com\/f\/readme"},
                          {"n":"more","e":[{"n":"INSTALL.txt","s":242117,"l":"https:\/\/alldebrid.com\/f\/install"}]}]}]}]}}"""

    private fun unlockAnswer(file: String) = """{"status":"success","data":{"link":"https://cdn.alldebrid.test/dl/$file","filename":"$file","host":"magnet","filesize":1}}"""

    @Test
    fun shouldListTheFilesOfAMagnetWithDirectLinks() {
        answer("v4/magnet/files", "id%5B%5D=56789", filesAnswer)
        answer("v4/link/unlock", "link=https%3A%2F%2Falldebrid.com%2Ff%2Fiso", unlockAnswer("iso"))
        answer("v4/link/unlock", "link=https%3A%2F%2Falldebrid.com%2Ff%2Freadme", unlockAnswer("readme"))
        answer("v4/link/unlock", "link=https%3A%2F%2Falldebrid.com%2Ff%2Finstall", unlockAnswer("install"))

        val files = alldebrid.getFilesFromTorrent(Torrent("Alldebrid").apply { remoteId = "56789" })

        assertThat(files.map { it.name }).containsExactly("ubuntu-18.04.2-live-server-amd64.iso", "docs/README.txt", "docs/more/INSTALL.txt")
        assertThat(files.map { it.url }).containsExactly(
            "https://cdn.alldebrid.test/dl/iso", "https://cdn.alldebrid.test/dl/readme", "https://cdn.alldebrid.test/dl/install"
        )
        assertThat(files.map { it.filesize }).containsExactly(145517304L, 87207L, 242117L)
    }

    @Test
    fun shouldListNoFilesIfOneCannotBeUnlocked() {
        answer("v4/magnet/files", "id%5B%5D=56789", filesAnswer)
        answer("v4/link/unlock", "link=https%3A%2F%2Falldebrid.com%2Ff%2Fiso", unlockAnswer("iso"))
        answer("v4/link/unlock", "link=https%3A%2F%2Falldebrid.com%2Ff%2Freadme",
            """{"status":"error","error":{"code":"LINK_DOWN","message":"This link is not available on the file hoster website"}}""")

        assertThat(alldebrid.getFilesFromTorrent(Torrent("Alldebrid").apply { remoteId = "56789" })).isEmpty()
    }

    @Test
    fun shouldWaitForADelayedLink() {
        answer("v4/link/unlock", """{"status":"success","data":{"filename":"a.mkv","host":"magnet","filesize":1,"delayed":42}}""")
        every { httpHelper.getPage("https://api.alldebrid.com/v4/link/delayed", "id=42", any()) } returnsMany listOf(
            """{"status":"success","data":{"status":1,"time_left":10}}""",
            """{"status":"success","data":{"status":2,"time_left":0,"link":"https://cdn.alldebrid.test/dl/a.mkv"}}"""
        )

        assertThat(alldebrid.unlock("https://alldebrid.com/f/a")).isEqualTo("https://cdn.alldebrid.test/dl/a.mkv")
    }

    @Test
    fun shouldGiveUpOnADelayedLinkThatFailsOrTakesTooLong() {
        answer("v4/link/unlock", """{"status":"success","data":{"filename":"a.mkv","delayed":42}}""")
        answer("v4/link/delayed", """{"status":"success","data":{"status":3,"time_left":0}}""")
        assertThat(alldebrid.unlock("https://alldebrid.com/f/a")).isNull()

        answer("v4/link/delayed", """{"status":"success","data":{"status":1,"time_left":10}}""")
        assertThat(alldebrid.unlock("https://alldebrid.com/f/a")).isNull()
        verify(exactly = 1 + 3) { httpHelper.getPage("https://api.alldebrid.com/v4/link/delayed", any<String>(), any()) }
    }

    @Test
    fun shouldDeleteAMagnetById() {
        answer("v4/magnet/delete", "id=56789", """{"status":"success","data":{"message":"Magnet was successfully deleted"}}""")

        alldebrid.delete(Torrent("Alldebrid").apply { remoteId = "56789" })

        verify { httpHelper.getPage("https://api.alldebrid.com/v4/magnet/delete", "id=56789", any()) }
    }

    @Test
    fun shouldHaveTrafficLeftOnlyWithPremiumOrATrialQuota() {
        answer("v4/user", """{"status":"success","data":{"user":{"username":"demoUserPremium","isPremium":true,"isTrial":false,"premiumUntil":1793597087,"limitedHostersQuotas":{"ddl":2000}}}}""")
        assertThat(alldebrid.getRemainingTrafficInMB()).isEqualTo(Alldebrid.UNLIMITED_TRAFFIC_MB)

        answer("v4/user", """{"status":"success","data":{"user":{"isPremium":false,"isTrial":true,"remainingTrialQuota":20480}}}""")
        assertThat(alldebrid.getRemainingTrafficInMB()).isEqualTo(20480.0)

        answer("v4/user", """{"status":"success","data":{"user":{"isPremium":false,"isTrial":false,"premiumUntil":0}}}""")
        assertThat(alldebrid.getRemainingTrafficInMB()).isEqualTo(0.0)

        answer("v4/user", """{"status":"error","error":{"code":"AUTH_BAD_APIKEY","message":"The auth apikey is invalid"}}""")
        assertThat(alldebrid.getRemainingTrafficInMB()).isEqualTo(0.0)
    }
}

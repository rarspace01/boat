package boat.info

import boat.utilities.HttpHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TheTVDBServiceTest {
    private var tvdbs: TheTVDBService? = null
    
    @BeforeEach
    fun beforeMethod() {
        tvdbs = TheTVDBService(HttpHelper())
    }

    @Tag("live")
    @Test
    fun search() {
        val mediaItems = tvdbs!!.search("MASH")!!
        assertThat(mediaItems).isNotEmpty
        assertThat(mediaItems.any { it.title == "M*A*S*H" && it.year == 1972 }).isTrue()
    }

    @Test
    fun parseResponsePage() {
        // Given
        val json = """
            {
              "status": "success",
              "data": [
                {
                  "name": "Bacozami Bap",
                  "type": "series",
                  "year": "2008"
                },
                {
                  "name": "Bacozami Bap: Original Soundtrack",
                  "type": "album",
                  "year": "2010"
                }
              ]
            }
        """.trimIndent()
        
        // When
        val items = tvdbs!!.parseResponsePage(json)
        
        // Then
        assertThat(items).isNotNull
        assertThat(items).hasSize(2)
        assertThat(items!![0].title).isEqualTo("Bacozami Bap")
        assertThat(items[0].type).isEqualTo(MediaType.Series)
        assertThat(items[0].year).isEqualTo(2008)
    }

    @Test
    fun parseResponsePageWithJapaneseAndSecondaryName() {
        // Given
        val json = """
            {
              "status": "success",
              "data": [
                {
                  "name": ".hack//黄昏の腕輪伝説",
                  "translations": {
                    "eng": ".hack//Legend of the Twilight",
                    "deu": ".hack//Legend of the Twilight"
                  },
                  "type": "series",
                  "year": "2003",
                  "tvdb_id": "457327"
                },
                {
                  "name": "進撃の巨人",
                  "secondary_name": "Amamad on Tilav",
                  "type": "series",
                  "year": "2013",
                  "tvdb_id": "267440"
                },
                {
                  "name": "鬼滅の刃",
                  "aliases": [
                    "Dozam Satebi: Kugunor no Ydamo"
                  ],
                  "type": "series",
                  "year": "2019",
                  "tvdb_id": "358554"
                }
              ]
            }
        """.trimIndent()

        // When
        val items = tvdbs!!.parseResponsePage(json)

        // Then
        assertThat(items).isNotNull
        assertThat(items).hasSize(3)

        // Item 1: from translations
        assertThat(items!![0].title).isEqualTo(".hack//Legend of the Twilight")
        assertThat(items[0].originalTitle).isEqualTo(".hack//黄昏の腕輪伝説")
        assertThat(items[0].type).isEqualTo(MediaType.Series)
        assertThat(items[0].id).isEqualTo("{tvdb-457327}")

        // Item 2: from secondary_name
        assertThat(items[1].title).isEqualTo("Amamad on Tilav")
        assertThat(items[1].originalTitle).isEqualTo("進撃の巨人")
        assertThat(items[1].type).isEqualTo(MediaType.Series)

        // Item 3: from aliases (ASCII fallback)
        assertThat(items[2].title).isEqualTo("Dozam Satebi: Kugunor no Ydamo")
        assertThat(items[2].originalTitle).isEqualTo("鬼滅の刃")
        assertThat(items[2].type).isEqualTo(MediaType.Series)
    }

    @Test
    fun parseResponsePageWithGermanOriginalSeriesAndMovie() {
        // Given
        val json = """
            {
              "status": "success",
              "data": [
                {
                  "name": "Dobi",
                  "primary_language": "deu",
                  "translations": {
                    "eng": "Dobi",
                    "deu": "Dobi"
                  },
                  "type": "series",
                  "year": "2017",
                  "tvdb_id": "334824"
                },
                {
                  "name": "Beredic Bapevo",
                  "primary_language": "deu",
                  "secondary_name": "Beredic Bapevo",
                  "translations": {
                    "eng": "Beredic Bapevo (EN)",
                    "deu": "Beredic Bapevo"
                  },
                  "type": "series",
                  "year": "2017",
                  "tvdb_id": "334825"
                },
                {
                  "name": "Das Bibo",
                  "original_language": "ger",
                  "translations": {
                    "eng": "The Boat",
                    "deu": "Das Bibo"
                  },
                  "type": "movie",
                  "year": "1981",
                  "tvdb_id": "1234"
                },
                {
                  "name": "How to Sell Drugs Online (Fast)",
                  "primary_country": "deu",
                  "translations": {
                    "eng": "How to Sell Drugs Online (Fast) (US)",
                    "deu": "How to Sell Drugs Online (Fast)"
                  },
                  "type": "series",
                  "year": "2019"
                },
                {
                  "name": "La Cepi de Pafag",
                  "primary_language": "spa",
                  "translations": {
                    "eng": "Metuv Hmeti",
                    "deu": "Hmud des Gifamo",
                    "spa": "La Cepi de Pafag"
                  },
                  "type": "series",
                  "year": "2017"
                }
              ]
            }
        """.trimIndent()

        // When
        val items = tvdbs!!.parseResponsePage(json)

        // Then
        assertThat(items).isNotNull
        assertThat(items).hasSize(5)

        // Item 1: German original series
        assertThat(items!![0].title).isEqualTo("Dobi")

        // Item 2: German original series with different English translation
        assertThat(items[1].title).isEqualTo("Beredic Bapevo")

        // Item 3: German original movie with English translation 'The Boat'
        assertThat(items[2].title).isEqualTo("Das Bibo")
        assertThat(items[2].type).isEqualTo(MediaType.Movie)

        // Item 4: German primary_country
        assertThat(items[3].title).isEqualTo("How to Sell Drugs Online (Fast)")

        // Item 5: Spanish original series (non-German) -> sticks with English name 'Metuv Hmeti'
        assertThat(items[4].title).isEqualTo("Metuv Hmeti")
    }
}

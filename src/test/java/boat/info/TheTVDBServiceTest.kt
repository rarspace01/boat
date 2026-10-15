package boat.info

import boat.utilities.HttpHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TheTVDBServiceTest {
    private var tvdbs: TheTVDBService? = null
    
    @BeforeEach
    fun beforeMethod() {
        tvdbs = TheTVDBService(HttpHelper())
    }

    @Test
    fun search() {
        val mediaItems = tvdbs!!.search("MASH")
        Assertions.assertTrue(mediaItems.isNotEmpty())
        Assertions.assertTrue(mediaItems.any { it.title == "M*A*S*H" && it.year == 1972 })
    }

    @Test
    fun parseResponsePage() {
        // Given
        val json = """
            {
              "status": "success",
              "data": [
                {
                  "name": "Breaking Bad",
                  "type": "series",
                  "year": "2008"
                },
                {
                  "name": "Breaking Bad: Original Soundtrack",
                  "type": "album",
                  "year": "2010"
                }
              ]
            }
        """.trimIndent()
        
        // When
        val items = tvdbs!!.parseResponsePage(json)
        
        // Then
        Assertions.assertNotNull(items)
        Assertions.assertEquals(2, items!!.size)
        Assertions.assertEquals("Breaking Bad", items[0].title)
        Assertions.assertEquals(MediaType.Series, items[0].type)
        Assertions.assertEquals(2008, items[0].year)
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
                  "secondary_name": "Attack on Titan",
                  "type": "series",
                  "year": "2013",
                  "tvdb_id": "267440"
                },
                {
                  "name": "鬼滅の刃",
                  "aliases": [
                    "Demon Slayer: Kimetsu no Yaiba"
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
        Assertions.assertNotNull(items)
        Assertions.assertEquals(3, items!!.size)

        // Item 1: from translations
        Assertions.assertEquals(".hack//Legend of the Twilight", items[0].title)
        Assertions.assertEquals(".hack//黄昏の腕輪伝説", items[0].originalTitle)
        Assertions.assertEquals(MediaType.Series, items[0].type)
        Assertions.assertEquals("{tvdb-457327}", items[0].id)

        // Item 2: from secondary_name
        Assertions.assertEquals("Attack on Titan", items[1].title)
        Assertions.assertEquals("進撃の巨人", items[1].originalTitle)
        Assertions.assertEquals(MediaType.Series, items[1].type)

        // Item 3: from aliases (ASCII fallback)
        Assertions.assertEquals("Demon Slayer: Kimetsu no Yaiba", items[2].title)
        Assertions.assertEquals("鬼滅の刃", items[2].originalTitle)
        Assertions.assertEquals(MediaType.Series, items[2].type)
    }

    @Test
    fun parseResponsePageWithGermanOriginalSeriesAndMovie() {
        // Given
        val json = """
            {
              "status": "success",
              "data": [
                {
                  "name": "Dark",
                  "primary_language": "deu",
                  "translations": {
                    "eng": "Dark",
                    "deu": "Dark"
                  },
                  "type": "series",
                  "year": "2017",
                  "tvdb_id": "334824"
                },
                {
                  "name": "Babylon Berlin",
                  "primary_language": "deu",
                  "secondary_name": "Babylon Berlin",
                  "translations": {
                    "eng": "Babylon Berlin (EN)",
                    "deu": "Babylon Berlin"
                  },
                  "type": "series",
                  "year": "2017",
                  "tvdb_id": "334825"
                },
                {
                  "name": "Das Boot",
                  "original_language": "ger",
                  "translations": {
                    "eng": "The Boat",
                    "deu": "Das Boot"
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
                  "name": "La Casa de Papel",
                  "primary_language": "spa",
                  "translations": {
                    "eng": "Money Heist",
                    "deu": "Haus des Geldes",
                    "spa": "La Casa de Papel"
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
        Assertions.assertNotNull(items)
        Assertions.assertEquals(5, items!!.size)

        // Item 1: German original series
        Assertions.assertEquals("Dark", items[0].title)

        // Item 2: German original series with different English translation
        Assertions.assertEquals("Babylon Berlin", items[1].title)

        // Item 3: German original movie with English translation 'The Boat'
        Assertions.assertEquals("Das Boot", items[2].title)
        Assertions.assertEquals(MediaType.Movie, items[2].type)

        // Item 4: German primary_country
        Assertions.assertEquals("How to Sell Drugs Online (Fast)", items[3].title)

        // Item 5: Spanish original series (non-German) -> sticks with English name 'Money Heist'
        Assertions.assertEquals("Money Heist", items[4].title)
    }
}

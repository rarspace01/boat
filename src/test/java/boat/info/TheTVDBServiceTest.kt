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
        // Given
        // When
        val mediaItems = tvdbs!!.search("Planet Earth")
        
        // Then
        // Note: This will fail if TVDB_APIKEY is not set in boat.cfg, but we want to see the result
        if (mediaItems != null) {
            println("TVDB Search results: ${mediaItems.size}")
            mediaItems.forEach { println(it) }
        }
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
}

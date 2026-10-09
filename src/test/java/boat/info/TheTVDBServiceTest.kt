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
}

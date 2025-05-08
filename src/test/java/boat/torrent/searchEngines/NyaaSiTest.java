package boat.torrent.searchEngines;

import boat.utilities.HttpHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NyaaSiTest {

    @Disabled
    @Test
    public void shouldFindTorrents() {
        // Given
        // When
        // Then
        assertDoesNotThrow(() -> new NyaaSi(new HttpHelper()).searchTorrents("planet"));
    }
}
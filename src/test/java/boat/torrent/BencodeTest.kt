package boat.torrent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BencodeTest {

    @Test
    fun shouldDecodeIntegersStringsListsAndDictionaries() {
        val decoded = Bencode.decode("d3:agei42e4:listl3:abci-7ee4:name4:Dunee".toByteArray()) as Map<*, *>

        assertThat(decoded["age"]).isEqualTo(42L)
        val list = decoded["list"] as List<*>
        assertThat(String(list[0] as ByteArray)).isEqualTo("abc")
        assertThat(list[1]).isEqualTo(-7L)
        assertThat(String(decoded["name"] as ByteArray)).isEqualTo("Dune")
    }

    @Test
    fun shouldKeepBinaryStringsIntact() {
        val bytes = byteArrayOf(0xff.toByte(), 0x80.toByte(), 0x00, 0x7f)
        val decoded = Bencode.decode("d4:key1".toByteArray() + "4:".toByteArray() + bytes + "e".toByteArray()) as Map<*, *>

        assertThat(decoded["key1"] as ByteArray).isEqualTo(bytes)
    }

    @Test
    fun shouldReturnNullForInvalidInput() {
        listOf("", "not bencode", "d3:key", "i12", "5:abc", "d3:keyi1e", "li1e", "i1ejunk").forEach {
            assertThat(Bencode.decode(it.toByteArray())).describedAs(it).isNull()
        }
    }
}

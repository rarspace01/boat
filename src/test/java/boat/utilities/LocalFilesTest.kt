package boat.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

internal class LocalFilesTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun shouldCreateAFile() {
        val file = dir.resolve("htpasswd")

        assertThat(LocalFiles.writeIfChanged(file, "a:hash\n")).isTrue()
        assertThat(Files.readString(file)).isEqualTo("a:hash\n")
        assertThat(LocalFiles.isOwnFile(file)).isTrue()
    }

    @Test
    fun shouldOnlyWriteAChangedContent() {
        val file = dir.resolve("htpasswd")
        LocalFiles.writeIfChanged(file, "a:hash\n")

        assertThat(LocalFiles.writeIfChanged(file, "a:hash\n")).isFalse()
        assertThat(LocalFiles.writeIfChanged(file, "b:hash\n")).isTrue()
        assertThat(Files.readString(file)).isEqualTo("b:hash\n")
    }

    @Test
    fun shouldKeepThePermissionsOfTheFile() {
        // e.g. made readable for the group of the reverse proxy only
        val file = dir.resolve("htpasswd")
        LocalFiles.writeIfChanged(file, "a:hash\n")
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"))

        LocalFiles.writeIfChanged(file, "b:hash\n")

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-r-----")
    }

    @Test
    fun shouldNotWriteThroughALink() {
        // another user of the machine links the file to one of boat's, or to one they can read
        val target = dir.resolve("target")
        Files.writeString(target, "keep")
        val link = Files.createSymbolicLink(dir.resolve("htpasswd"), target)

        assertThrows(IOException::class.java) { LocalFiles.writeIfChanged(link, "a:hash\n") }
        assertThat(Files.readString(target)).isEqualTo("keep")
        assertThat(LocalFiles.isOwnFile(link)).isFalse()
    }

    @Test
    fun shouldNotTakeAMissingFileOrAFolderAsOwnFile() {
        assertThat(LocalFiles.isOwnFile(dir.resolve("missing"))).isFalse()
        assertThat(LocalFiles.isOwnFile(dir)).isFalse()
    }
}

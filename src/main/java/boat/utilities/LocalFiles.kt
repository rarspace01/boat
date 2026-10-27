package boat.utilities

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.UserPrincipal

/**
 * Files in folders other users of the machine can write to as well, like /tmp: one of them could put a file or a link
 * there before boat does, to read what boat writes or to have boat read what they wrote.
 */
object LocalFiles {

    // from a file boat made itself, user.name isn't always the name the files get (e.g. a container without that user)
    private val currentUser: UserPrincipal by lazy {
        val probe = Files.createTempFile("boat-owner", ".tmp")
        try {
            Files.getOwner(probe)
        } finally {
            Files.deleteIfExists(probe)
        }
    }

    /** Whether [path] is a file (not a link) of the user boat runs as, false if there is none. */
    fun isOwnFile(path: Path): Boolean =
        Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.getOwner(path, NOFOLLOW_LINKS) == currentUser

    /**
     * Writes [content] to [path] if it has another content, returns whether it wrote. A link or a file of another user is
     * refused ([IOException]). The file is written in place, so the permissions it was given stay; a new one is created
     * with CREATE_NEW, so nobody can put one there in between.
     */
    fun writeIfChanged(path: Path, content: String): Boolean {
        if (Files.exists(path, NOFOLLOW_LINKS)) {
            if (!isOwnFile(path)) throw IOException("[$path] is a link or a file of another user, not writing it")
            if (Files.readString(path) == content) return false
            Files.writeString(path, content, WRITE, TRUNCATE_EXISTING, NOFOLLOW_LINKS)
        } else {
            Files.writeString(path, content, CREATE_NEW, WRITE, NOFOLLOW_LINKS)
        }
        return true
    }
}

package boat.info

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CloudFileServiceCommandTest {

    @Test
    fun shouldPassPathWithShellCharactersAsOneArgument() {
        val path = "remote:Movies/A/'; rm -rf ~; echo '/"

        assertThat(CloudFileService.buildLsjsonCommand(path))
            .containsExactly("rclone", "--no-check-certificate", "lsjson", "--recursive", path)
    }

    @Test
    fun shouldListTransferFolderNonRecursive() {
        assertThat(CloudFileService.buildLsjsonCommand("remote:/transfer/"))
            .containsExactly("rclone", "--no-check-certificate", "lsjson", "remote:/transfer/")
    }
}

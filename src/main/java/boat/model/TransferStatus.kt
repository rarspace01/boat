package boat.model

enum class TransferStatus(val string: String) {
    NONE("\uD83C\uDD93"), UNKNOWN("⁉"), ADDED("➕"), ADDED_TO_MULTIHOSTER("\uD83C\uDFE0➕"), DOWNLOADING_TO_MULTIHOSTER("\uD83C\uDFE0⬇️"), UPLOADING_TO_MULTIHOSTER(
        "\uD83C\uDFE0⬆️"
    ),
    READY_TO_BE_DOWNLOADED("\uD83C\uDFE0\uD83C\uDF86"), UPLOADING_TO_DRIVE("☁️⬆️"), UPLOADED("☁️✔️"), ERROR("⛔"), SERVER_ERROR("\uD83D\uDDA5\uD83D\uDC1B"), DELETED(
        "\uD83E\uDDF9"
    )
}
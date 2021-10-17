package boat.model

enum class TransferStatus {
    NONE, UNKNOWN, ADDED, ADDED_TO_MULTIHOSTER, DOWNLOADING_TO_MULTIHOSTER, UPLOADING_TO_MULTIHOSTER, READY_TO_BE_DOWNLOADED, UPLOADING_TO_DRIVE, UPLOADED, ERROR, SERVER_ERROR, DELETED
}
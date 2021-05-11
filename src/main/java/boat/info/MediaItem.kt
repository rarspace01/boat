package boat.info

data class MediaItem(
    val title: String,
    var originalTitle: String? = null,
    val year: Int? = null,
    var type: MediaType? = null
)
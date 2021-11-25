package boat.info

data class MediaItem(
    val title: String,
    var originalTitle: String? = null,
    val year: Int? = null,
    var type: MediaType? = null
) {
    override fun toString(): String {
        return StringBuilder().append("[$title]")
            .append(if (originalTitle != null) "[$originalTitle]" else "")
            .append(if (year != null) "[$year]" else "")
            .append(if (type != null) "[$type]" else "")
            .append("\n")
            .toString()
    }
}

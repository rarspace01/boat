package boat.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "user")
data class User(
    @Id
    var id: String? = null,
    val username: String,
    var password: String,
    val roles: List<String> = listOf("USER"),
    // null until the first login after this field was introduced
    val lastLogin: Instant? = null
)

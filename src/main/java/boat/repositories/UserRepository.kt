package boat.repositories

import boat.model.User
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.Update
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface UserRepository : MongoRepository<User, String> {
    fun findByUsername(username: String): User?

    /**
     * Sets only the last login of the user, the rest of the document (e.g. a password changed in the meantime) is left
     * as it is. Returns the number of updated users.
     */
    @Query("{ 'username': ?0 }")
    @Update("{ '\$set': { 'lastLogin': ?1 } }")
    fun updateLastLoginByUsername(username: String, lastLogin: Instant): Long
}

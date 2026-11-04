package boat

import boat.info.MediaProxyService
import boat.model.User
import boat.repositories.UserRepository
import boat.security.CachedAuthenticationProvider
import boat.utilities.LoggerDelegate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Administration of boat, only for users with the role ADMIN (see [SecurityConfiguration]): add users, reset passwords
 * and clear the media cache.
 */
@RestController
@RequestMapping("/boat/admin")
class AdminController @Autowired constructor(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val cachedAuthenticationProvider: CachedAuthenticationProvider,
    private val mediaProxyService: MediaProxyService,
) {

    companion object {
        private val logger by LoggerDelegate()
        const val ADMIN_ROLE = "ADMIN"
        const val MIN_PASSWORD_LENGTH = 8
        // no ':' (separator of the htpasswd file) and no whitespace
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9._@-]{1,64}")

        fun isAdmin(authentication: Authentication?): Boolean =
            authentication?.authorities?.any { it.authority == "ROLE_$ADMIN_ROLE" } == true

        private val LAST_LOGIN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

        /** Null until the user logged in the first time after the last login was introduced. */
        internal fun lastLoginText(lastLogin: Instant?): String = lastLogin?.let { LAST_LOGIN_FORMAT.format(it) } ?: "unknown"

        internal fun cacheStatisticsHtml(stats: MediaProxyService.CacheStatistics): String =
            "entries: ${stats.entries} of ${stats.maxEntries} (${stats.expiredEntries} expired)<br/>" +
                    "media items: ${stats.items}<br/>" +
                    "oldest entry: ${stats.oldestCachedAt?.let { LAST_LOGIN_FORMAT.format(Instant.ofEpochMilli(it)) } ?: "none"}<br/>"

        private fun urlEncode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    private val page = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
    body { font-size: 1.5em; overflow-wrap: break-word; word-wrap: break-word; }
    input[type="text"], input[type="password"] { font-size: 1em; box-sizing: border-box; }
    @media (min-width: 800px) { body { font-size: 1em; } }
</style>
</head>
<body>
"""

    @GetMapping("")
    fun admin(@RequestParam(value = "message", required = false) message: String?): String {
        val users = userRepository.findAll().sortedBy { it.username }
        return page +
                (message?.takeIf { it.isNotBlank() }?.let { "<b>" + HtmlUtils.htmlEscape(it) + "</b><br/>" } ?: "") +
                "<a href=\"./info\">Back to info</a>" +
                "<h3>Add user</h3>" +
                """<form method="post" action="./admin/addUser">""" +
                """<input type="text" name="username" placeholder="username" required maxlength="64"> """ +
                """<input type="password" name="password" placeholder="password" required minlength="$MIN_PASSWORD_LENGTH" autocomplete="new-password"> """ +
                """<button type="submit">add</button></form>""" +
                "<h3>Reset password</h3>" +
                """<form method="post" action="./admin/resetPassword">""" +
                """<input type="text" name="username" placeholder="username" required maxlength="64"> """ +
                """<input type="password" name="password" placeholder="new password" required minlength="$MIN_PASSWORD_LENGTH" autocomplete="new-password"> """ +
                """<button type="submit">reset</button></form>""" +
                "<h3>Media cache</h3>" + cacheStatisticsHtml(mediaProxyService.cacheStatistics()) +
                """<form method="post" action="./admin/clearMediaCache"><button type="submit">clear media cache</button></form>""" +
                "<h3>Users [${users.size}]</h3>" +
                users.joinToString("<br/>") {
                    val name = HtmlUtils.htmlEscape(it.username)
                    name + " (" + HtmlUtils.htmlEscape(it.roles.joinToString()) + "), last login: " + lastLoginText(it.lastLogin) +
                            """ <form method="post" action="./admin/deleteUser" style="display:inline">""" +
                            """<input type="hidden" name="username" value="$name"><button type="submit" title="delete">🗑</button></form>"""
                } +
                "</body>\n</html>\n"
    }

    @PostMapping("/addUser")
    fun addUser(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
    ): ResponseEntity<Void> {
        val name = username.trim()
        val error = validateUsername(name) ?: validatePassword(password)
        if (error != null) return redirect(error)
        if (userRepository.findByUsername(name) != null) return redirect("user [$name] already exists")
        userRepository.save(User(username = name, password = passwordEncoder.encode(password)!!))
        logger.info("added user [{}]", name)
        return redirect("added user [$name]")
    }

    @PostMapping("/resetPassword")
    fun resetPassword(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
    ): ResponseEntity<Void> {
        val name = username.trim()
        validatePassword(password)?.let { return redirect(it) }
        val user = userRepository.findByUsername(name) ?: return redirect("user [$name] not found")
        user.password = passwordEncoder.encode(password)!!
        userRepository.save(user)
        // logins accepted with the old password are valid for 10 minutes otherwise
        cachedAuthenticationProvider.clearAuthenticationCache()
        logger.info("reset the password of user [{}]", name)
        return redirect("reset the password of [$name]")
    }

    @PostMapping("/deleteUser")
    fun deleteUser(@RequestParam("username") username: String, authentication: Authentication?): ResponseEntity<Void> {
        val name = username.trim()
        if (name == authentication?.name) return redirect("you can't delete yourself")
        val user = userRepository.findByUsername(name) ?: return redirect("user [$name] not found")
        if (ADMIN_ROLE in user.roles && userRepository.findAll().count { ADMIN_ROLE in it.roles } <= 1) {
            return redirect("the last admin can't be deleted")
        }
        userRepository.delete(user)
        // logins accepted before are valid for 10 minutes otherwise
        cachedAuthenticationProvider.clearAuthenticationCache()
        logger.info("deleted user [{}]", name)
        return redirect("deleted user [$name]")
    }

    @PostMapping("/clearMediaCache")
    fun clearMediaCache(): ResponseEntity<Void> {
        logger.info("clear media cache request received (admin)")
        return redirect("cleared media cache, removed ${mediaProxyService.clearCache()} entries")
    }

    private fun validateUsername(username: String): String? =
        if (USERNAME_PATTERN.matches(username)) null
        else "invalid username, use 1-64 letters, digits or . _ @ -"

    private fun validatePassword(password: String): String? =
        if (password.length >= MIN_PASSWORD_LENGTH) null else "the password needs at least $MIN_PASSWORD_LENGTH characters"

    // 303, so reloading the page doesn't send the form again
    private fun redirect(message: String): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.SEE_OTHER)
            .header(HttpHeaders.LOCATION, "../admin?message=" + urlEncode(message))
            .build()
}

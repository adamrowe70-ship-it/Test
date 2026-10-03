package runcoach.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import runcoach.core.PlaylistPlan
import runcoach.core.Track
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Spotify Web API over OAuth PKCE (no client secret on the device).
 *
 * Only uses endpoints that Development Mode apps can still call after the 2024 and
 * February 2026 API changes: your top tracks, saved and recent tracks, `POST /me/playlists`
 * and `POST /playlists/{id}/items`. Spotify no longer gives new apps song tempo, so
 * [TempoProvider] supplies the BPM.
 */
class Spotify(private val store: Store, private val clientId: String) {
    companion object {
        const val REDIRECT_URI = "runcoach://callback"
        private const val SCOPES =
            "user-top-read user-library-read user-read-recently-played playlist-modify-private"
        private const val API = "https://api.spotify.com/v1"
    }

    val isConnected: Boolean get() = store.spotifyRefreshToken != null

    fun startLogin(context: Context) {
        require(clientId.isNotBlank()) { "Set spotify.clientId in local.properties" }
        val verifier = randomString(64)
        store.pkceVerifier = verifier
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .build()
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    }

    /** Called with the runcoach://callback?code=… redirect. */
    suspend fun finishLogin(redirect: Uri) {
        val code = redirect.getQueryParameter("code")
            ?: error("Spotify login failed: ${redirect.getQueryParameter("error")}")
        val verifier = store.pkceVerifier ?: error("No login in progress")
        tokenRequest(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", clientId)
                .add("code_verifier", verifier)
                .build()
        )
        store.pkceVerifier = null
    }

    private suspend fun tokenRequest(form: FormBody) {
        val res = execute(Request.Builder().url("https://accounts.spotify.com/api/token").post(form).build())
        store.spotifyAccessToken = res["access_token"].str
        res["refresh_token"].str?.let { store.spotifyRefreshToken = it }
        store.spotifyTokenExpiry = System.currentTimeMillis() + (res["expires_in"].lng ?: 3600) * 1000 - 60_000
    }

    private suspend fun accessToken(): String {
        if (store.spotifyAccessToken == null || System.currentTimeMillis() > store.spotifyTokenExpiry) {
            val refresh = store.spotifyRefreshToken ?: error("Connect Spotify first")
            tokenRequest(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .add("client_id", clientId)
                    .build()
            )
        }
        return store.spotifyAccessToken!!
    }

    private suspend fun get(path: String) =
        execute(Request.Builder().url("$API$path").header("Authorization", "Bearer ${accessToken()}").build())

    private suspend fun post(path: String, body: String) = execute(
        Request.Builder().url("$API$path")
            .header("Authorization", "Bearer ${accessToken()}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    )

    /**
     * Music you actually like: your top tracks over three time ranges, then saved tracks,
     * then recent plays. [Track.tasteRank] follows that order.
     */
    suspend fun tastePool(maxSaved: Int = 200): List<Track> {
        val out = mutableListOf<Track>()
        fun add(item: kotlinx.serialization.json.JsonElement?) {
            val t = item ?: return
            val uri = t["uri"].str ?: return
            if (t["is_local"]?.toString() == "true") return
            val artist = (t["artists"] as? JsonArray)?.firstOrNull()?.get("name").str ?: ""
            out += Track(uri, t["name"].str ?: "", artist, t["duration_ms"].lng ?: 0, tasteRank = out.size)
        }
        for (range in listOf("short_term", "medium_term", "long_term")) {
            (get("/me/top/tracks?time_range=$range&limit=50")["items"] as? JsonArray)?.forEach { add(it) }
        }
        var offset = 0
        while (offset < maxSaved) {
            val items = get("/me/tracks?limit=50&offset=$offset")["items"] as? JsonArray ?: break
            items.forEach { add(it["track"]) }
            if (items.size < 50) break
            offset += 50
        }
        (get("/me/player/recently-played?limit=50")["items"] as? JsonArray)?.forEach { add(it["track"]) }
        return out.distinctBy { it.uri }
    }

    /** Creates a private playlist and returns its Spotify URL. */
    suspend fun createPlaylist(plan: PlaylistPlan): String {
        val created = post(
            "/me/playlists",
            buildJsonObject {
                put("name", plan.name)
                put("description", plan.description)
                put("public", false)
            }.toString(),
        )
        val id = created["id"].str ?: error("Spotify did not return a playlist id")
        plan.tracks.map { it.track.uri }.chunked(100).forEach { uris ->
            post("/playlists/$id/items", buildJsonObject {
                put("uris", JsonArray(uris.map { JsonPrimitive(it) }))
            }.toString())
        }
        return created["external_urls"]?.get("spotify").str ?: "https://open.spotify.com/playlist/$id"
    }

    private fun randomString(len: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val rnd = SecureRandom()
        return (1..len).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }
}

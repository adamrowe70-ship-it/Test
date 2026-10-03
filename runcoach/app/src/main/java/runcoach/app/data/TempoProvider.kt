package runcoach.app.data

import kotlinx.serialization.json.JsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import runcoach.core.Track

/**
 * Song tempo (BPM) by Spotify track ID from ReccoBeats (https://reccobeats.com). It is free
 * and needs no API key. Results are cached on the device, so each song is looked up only once.
 */
class TempoProvider(private val store: Store) {
    private data class Features(val bpm: Double, val energy: Double?)

    suspend fun withTempo(tracks: List<Track>): List<Track> {
        val cache = store.tempoCache
        val missing = tracks.map { it.spotifyId() }.filter { it !in cache }.distinct()
        for (batch in missing.chunked(40)) {
            // On a network error, leave the batch uncached so it is retried next time.
            val found = runCatching { fetch(batch) }.getOrNull() ?: continue
            batch.forEach { id -> cache[id] = found[id]?.let { "${it.bpm},${it.energy ?: ""}" } ?: "" }
        }
        store.tempoCache = cache
        return tracks.map { t ->
            val parts = cache[t.spotifyId()].orEmpty().split(",")
            t.copy(bpm = parts.getOrNull(0)?.toDoubleOrNull(), energy = parts.getOrNull(1)?.toDoubleOrNull())
        }
    }

    private suspend fun fetch(spotifyIds: List<String>): Map<String, Features> {
        val url = "https://api.reccobeats.com/v1/audio-features".toHttpUrl().newBuilder()
            .addQueryParameter("ids", spotifyIds.joinToString(","))
            .build()
        val content = execute(Request.Builder().url(url).header("Accept", "application/json").build())["content"]
            as? JsonArray ?: return emptyMap()
        return content.mapNotNull { f ->
            // Each result links back to the Spotify track it describes.
            val id = f["href"].str?.substringAfterLast("/")?.substringBefore("?") ?: return@mapNotNull null
            val bpm = f["tempo"].dbl?.takeIf { it > 0 } ?: return@mapNotNull null
            id to Features(bpm, f["energy"].dbl)
        }.toMap()
    }

    private fun Track.spotifyId() = uri.substringAfterLast(":")
}

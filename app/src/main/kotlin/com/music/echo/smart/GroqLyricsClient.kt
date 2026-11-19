package echo.music.iad1tya.smart

import echo.music.iad1tya.cinematic.CinematicMood
import echo.music.iad1tya.cinematic.CinematicTheme
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Parsed, cached-friendly lyrics intelligence returned by the secure backend. */
data class AiAnalysis(
  val mood: CinematicMood = CinematicMood.NEUTRAL,
  val confidence: Float = 0f,
  val energyHint: Float = 0.5f,
  val genreTags: List<String> = emptyList(),
  val themeHint: CinematicTheme? = null,
  val sections: List<AiSection> = emptyList(),
  val explanation: String = "",
  val degraded: Boolean = false,
  val rawJson: String = "{}",
)

data class AiSection(val name: String, val startMs: Long, val endMs: Long)

/**
 * Android-side client for the operator-run backend. The Groq key never enters
 * the APK: this client only talks to `GROQ_API_KEY`-backed `/api/groq/analyze`.
 * Network failure returns a degraded result so playback is never interrupted.
 */
class GroqLyricsClient {
  suspend fun analyze(
    baseUrl: String,
    clientToken: String,
    model: String,
    title: String,
    artist: String,
    lyrics: String,
    genreTags: List<String>,
  ): AiAnalysis =
    withContext(Dispatchers.IO) {
      val url = baseUrl.trim().trimEnd('/') + "/api/groq/analyze"
      val body =
        JSONObject()
          .put("title", title)
          .put("artist", artist)
          .put("lyrics", lyrics.take(12_000))
          .put("model", model)
          .put(
            "genre_tags",
            JSONArray().apply { genreTags.take(16).forEach { put(it) } },
          )
          .toString()
      var connection: HttpURLConnection? = null
      try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 6_000
          readTimeout = 15_000
          setRequestProperty("Content-Type", "application/json")
          setRequestProperty("Accept", "application/json")
          if (clientToken.isNotBlank()) {
            setRequestProperty("Authorization", "Bearer $clientToken")
          }
          doOutput = true
        }
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) return@withContext AiAnalysis(degraded = true, explanation = "Backend HTTP $code")
        parse(text)
      } catch (e: IOException) {
        AiAnalysis(degraded = true, explanation = "Backend unavailable; using cached/local intelligence")
      } catch (e: Exception) {
        AiAnalysis(degraded = true, explanation = "Analysis failed; using cached/local intelligence")
      } finally {
        connection?.disconnect()
      }
    }

  fun parse(json: String): AiAnalysis =
    runCatching {
      val obj = JSONObject(json)
      val moodName = obj.optString("mood", "NEUTRAL")
      val mood = runCatching { CinematicMood.valueOf(moodName) }.getOrDefault(CinematicMood.NEUTRAL)
      val themeHint =
        obj.optString("theme_hint", "").takeIf { it.isNotBlank() }?.let { name ->
          runCatching { CinematicTheme.valueOf(name) }.getOrNull()
        }
      val tags = JSONArray(obj.optString("genre_tags", "[]").let { raw ->
        if (raw.startsWith("[")) raw else "[]"
      })
      val sections = JSONArray(obj.optString("sections", "[]").let { raw ->
        if (raw.startsWith("[")) raw else "[]"
      })
      AiAnalysis(
        mood = mood,
        confidence = obj.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
        energyHint = obj.optDouble("energy_hint", 0.5).toFloat().coerceIn(0f, 1f),
        genreTags =
          (0 until tags.length()).mapNotNull { runCatching { tags.getString(it) }.getOrNull() },
        themeHint = themeHint,
        sections =
          (0 until sections.length()).mapNotNull { index ->
            val section = sections.optJSONObject(index) ?: return@mapNotNull null
            val rawName = section.optString("name", "VERSE").trim().uppercase(Locale.US)
            val canonical =
              listOf("CHORUS", "HOOK", "DROP", "REFRAIN", "VERSE", "BRIDGE", "BUILD", "INTRO", "OUTRO")
                .firstOrNull { rawName.contains(it) } ?: "VERSE"
            AiSection(
              name = canonical,
              startMs = section.optLong("start_ms", 0L),
              endMs = section.optLong("end_ms", 0L),
            )
          },
        explanation = obj.optString("explanation", ""),
        degraded = obj.optBoolean("degraded", false),
        rawJson = json,
      )
    }.getOrElse { AiAnalysis(degraded = true, explanation = "Malformed analysis response") }
}

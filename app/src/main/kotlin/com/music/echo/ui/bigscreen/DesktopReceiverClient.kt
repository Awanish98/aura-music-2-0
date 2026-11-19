package echo.music.iad1tya.ui.bigscreen

import echo.music.iad1tya.ui.component.KaraokeWord
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DesktopPairingSession(
  val code: String,
  val sessionId: String,
  val sessionToken: String,
  val receiverUrl: String,
  val expiresAt: Long,
)

data class ReceiverSessionStatus(
  val paired: Boolean,
  val hasState: Boolean,
  val lastPollAt: Long?,
  val receiverName: String,
  val expiresAt: Long,
)

/** Talks only to the operator's secure backend; no tokens are hardcoded here. */
class DesktopReceiverClient {
  suspend fun createSession(
    baseUrl: String,
    clientToken: String,
    receiverName: String,
  ): DesktopPairingSession? =
    withContext(Dispatchers.IO) {
      request(
        baseUrl,
        clientToken,
        "/api/receiver/create",
        JSONObject().put("name", receiverName).toString(),
      )?.let { obj ->
        DesktopPairingSession(
          code = obj.optString("code", ""),
          sessionId = obj.optString("session_id", ""),
          sessionToken = obj.optString("session_token", obj.optString("token", "")),
          receiverUrl = obj.optString("receiver_url", ""),
          expiresAt = obj.optLong("expires_at", 0L),
        )
      }
    }

  suspend fun status(
    baseUrl: String,
    clientToken: String,
    sessionToken: String,
  ): ReceiverSessionStatus? =
    withContext(Dispatchers.IO) {
      if (baseUrl.isBlank() || sessionToken.isBlank()) return@withContext null
      val encoded = URLEncoder.encode(sessionToken, StandardCharsets.UTF_8.name())
      val url = baseUrl.trim().trimEnd('/') + "/api/receiver/status?session_token=$encoded"
      var connection: HttpURLConnection? = null
      try {
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 5_000
          readTimeout = 8_000
          setRequestProperty("Accept", "application/json")
          if (clientToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $clientToken")
        }
        if (connection.responseCode != 200) return@withContext null
        val obj = JSONObject(connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() })
        ReceiverSessionStatus(
          paired = obj.optBoolean("paired", false),
          hasState = obj.optBoolean("has_state", false),
          lastPollAt = obj.optLong("last_poll_at", 0L).takeIf { it > 0L },
          receiverName = obj.optString("receiver_name", ""),
          expiresAt = obj.optLong("expires_at", 0L),
        )
      } catch (_: Exception) {
        null
      } finally {
        connection?.disconnect()
      }
    }

  suspend fun publish(
    baseUrl: String,
    clientToken: String,
    sessionToken: String,
    state: BigScreenVisualState,
    durationMs: Long = 0L,
    playing: Boolean = true,
    lyricsText: String = "",
  ): Boolean =
    withContext(Dispatchers.IO) {
      val visual = state.visual
      val theme = visual.theme
      val body =
        JSONObject()
          .put("session_token", sessionToken)
          .put("theme_name", theme.name)
          .put(
            "theme",
            JSONObject()
              .put("background", hex(theme.background))
              .put("primary", hex(theme.primary))
              .put("secondary", hex(theme.secondary))
              .put("accent", hex(theme.accent))
              .put("text", hex(theme.text))
              .put("muted", hex(theme.muted)),
          )
          .put(
            "features",
            JSONObject()
              .put("bass", visual.features.bass)
              .put("beat", visual.features.beat)
              .put("energy", visual.features.energy)
              .put("tempo", (visual.features.bpm / 200f).coerceIn(0f, 1f)),
          )
          .put("position_ms", visual.positionMs)
          .put("duration_ms", durationMs)
          .put("playing", playing)
          .put("title", state.title)
          .put("artist", state.artist)
          .put("lyrics", lyricsText)
          .put("lyrics_visible", state.lyricsVisible)
          .put(
            "words",
            JSONArray().apply {
              for (word in state.words.take(80)) {
                put(
                  JSONObject()
                    .put("text", word.text)
                    .put("start_ms", word.startMs)
                    .put("end_ms", word.endMs)
                )
              }
            },
          )
          .toString()
      request(baseUrl, clientToken, "/api/receiver/publish", body) != null
    }

  suspend fun disconnect(
    baseUrl: String,
    clientToken: String,
    sessionToken: String,
  ): Boolean =
    withContext(Dispatchers.IO) {
      request(
        baseUrl,
        clientToken,
        "/api/receiver/disconnect",
        JSONObject().put("session_token", sessionToken).toString(),
      ) != null
    }

  private fun request(
    baseUrl: String,
    clientToken: String,
    path: String,
    body: String,
  ): JSONObject? {
    if (baseUrl.isBlank()) return null
    val url = baseUrl.trim().trimEnd('/') + path
    var connection: HttpURLConnection? = null
    try {
      connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 5_000
        readTimeout = 8_000
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        if (clientToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $clientToken")
        doOutput = true
      }
      connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
      val code = connection.responseCode
      val stream = connection.inputStream ?: connection.errorStream
      val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
      if (code !in 200..299) return null
      if (text.isBlank()) return JSONObject()
      return runCatching { JSONObject(text) }.getOrNull()
    } catch (_: IOException) {
      return null
    } finally {
      connection?.disconnect()
    }
  }
}

private fun hex(value: Long): String = String.format("#%06X", 0xFFFFFF and value.toInt())

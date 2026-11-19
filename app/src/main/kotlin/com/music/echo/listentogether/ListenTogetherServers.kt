package echo.music.iad1tya.listentogether

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ListenTogetherServer(
  val name: String,
  val url: String,
  val location: String,
  val operator: String
)

object ListenTogetherServers {
  // No remote server directory is fetched: no legacy distribution endpoint is contacted.

  private val _servers = MutableStateFlow(emptyList<ListenTogetherServer>())

  val serversFlow: StateFlow<List<ListenTogetherServer>> = _servers

  val servers: List<ListenTogetherServer>
    get() = _servers.value

  val defaultServerUrl: String
    get() = ""

  fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }
}

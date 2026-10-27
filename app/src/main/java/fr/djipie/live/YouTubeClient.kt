package fr.djipie.live

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ActiveChat(val chatId: String, val title: String)
data class ChatBatch(val items: List<ChatLine>, val nextPageToken: String?, val waitMs: Long)
class ApiFailure(val code: Int) : IOException("Code HTTP " + code)

object YouTubeClient {
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun get(path: String, token: String): JSONObject {
        val connection = URL("https://www.googleapis.com/youtube/v3/" + path).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 12000
        connection.setRequestProperty("Authorization", "Bearer " + token)
        connection.setRequestProperty("Accept", "application/json")
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw ApiFailure(responseCode)
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    // The owner's streams only; Google provides the liveChatId in snippet.
    fun currentChat(token: String): ActiveChat? {
        var next: String? = null
        repeat(3) {
            val args = "liveBroadcasts?part=snippet%2Cstatus&mine=true&broadcastType=all&maxResults=50" +
                (next?.let { "&pageToken=" + enc(it) } ?: "")
            val json = get(args, token)
            val items = json.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val status = item.optJSONObject("status")?.optString("lifeCycleStatus").orEmpty()
                val snippet = item.optJSONObject("snippet") ?: continue
                val chatId = snippet.optString("liveChatId")
                if (status == "live" && chatId.isNotBlank()) {
                    return ActiveChat(chatId, snippet.optString("title", "Mon direct YouTube"))
                }
            }
            next = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (next == null) return null
        }
        return null
    }

    fun messages(token: String, chatId: String, pageToken: String?): ChatBatch {
        val args = "liveChat/messages?part=snippet%2CauthorDetails&liveChatId=" + enc(chatId) +
            "&maxResults=200" + (pageToken?.let { "&pageToken=" + enc(it) } ?: "")
        val response = get(args, token)
        val items = response.optJSONArray("items")
        val lines = mutableListOf<ChatLine>()
        if (items != null) for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val snippet = obj.optJSONObject("snippet") ?: continue
            if (snippet.optString("type") != "textMessageEvent") continue
            val author = obj.optJSONObject("authorDetails")?.optString("displayName", "Spectateur")
                ?.take(60).orEmpty().ifBlank { "Spectateur" }
            val text = ChatPolicy.clean(snippet.optString("displayMessage"))
            if (text.isNotEmpty()) lines.add(ChatLine(obj.optString("id"), author, text))
        }
        val next = response.optString("nextPageToken").takeIf { it.isNotBlank() }
        // Respect Google's server-provided minimum interval; add an 8s budget-friendly floor.
        val wait = maxOf(response.optLong("pollingIntervalMillis", 8000), 8000L).coerceAtMost(60000L)
        return ChatBatch(lines, next, wait)
    }
}

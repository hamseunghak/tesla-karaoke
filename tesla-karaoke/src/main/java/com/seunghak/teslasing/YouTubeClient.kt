package com.seunghak.teslasing

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
    val requester: String = "",
    val playCount: Int = 0,
    val lookupQuery: String = "",
    val chartRank: Int = 0
)

object YouTubeClient {
    fun search(apiKey: String, query: String): List<YouTubeVideo> {
        require(apiKey.isNotBlank()) { "YouTube API 키를 입력해주세요." }
        require(query.isNotBlank()) { "검색어를 입력해주세요." }

        val normalizedQuery = query.trim()
        val karaokeQuery = if (goldenSingMarkers.any { normalizedQuery.contains(it, ignoreCase = true) }) {
            normalizedQuery
        } else {
            "$normalizedQuery 금영 노래방 KY karaoke"
        }
        return requestVideos(apiKey, karaokeQuery, "relevance")
    }

    private fun requestVideos(
        apiKey: String,
        query: String,
        order: String
    ): List<YouTubeVideo> {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val encodedKey = URLEncoder.encode(apiKey.trim(), Charsets.UTF_8.name())
        val endpoint = "https://www.googleapis.com/youtube/v3/search" +
            "?part=snippet&type=video&videoEmbeddable=true&safeSearch=moderate" +
            "&regionCode=KR&relevanceLanguage=ko&maxResults=25&order=$order" +
            "&q=$encodedQuery&key=$encodedKey"

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault("YouTube 검색에 실패했습니다. ($status)")
                error(message)
            }
            val items = JSONObject(body).getJSONArray("items")
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val videoId = item.getJSONObject("id").optString("videoId")
                    if (videoId.isBlank()) continue
                    val snippet = item.getJSONObject("snippet")
                    add(
                        YouTubeVideo(
                            videoId = videoId,
                            title = decode(snippet.optString("title")),
                            channel = decode(snippet.optString("channelTitle")),
                            thumbnailUrl = snippet.optJSONObject("thumbnails")
                                ?.optJSONObject("medium")?.optString("url").orEmpty()
                        )
                    )
                }
            }.filter { video ->
                val searchable = "${video.title} ${video.channel}"
                val isKaraoke = karaokeMarkers.any {
                    searchable.contains(it, ignoreCase = true)
                }
                val isTjContent = tjMarkers.any {
                    searchable.contains(it, ignoreCase = true)
                }
                isKaraoke && !isTjContent
            }.take(15)
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun decode(value: String): String =
        Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()

    private val karaokeMarkers = listOf(
        "노래방", "karaoke", "MR", "반주", "instrumental", "TJ", "금영", "KY"
    )

    private val goldenSingMarkers = listOf(
        "금영", "KY", "Kumyoung"
    )

    private val tjMarkers = listOf(
        "TJ노래방",
        "TJ Karaoke",
        "Ziller - TJ Communication",
        "TJ미디어"
    )
}

package com.seunghak.teslasing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object YouTubeLibrary {
    private const val PREFS_NAME = "tesla_sing_library"
    private const val FAVORITES = "favorites"
    private const val HISTORY = "history"
    private const val QUEUE = "queue"

    fun favorites(context: Context): List<YouTubeVideo> = load(context, FAVORITES)
    fun history(context: Context): List<YouTubeVideo> = load(context, HISTORY)
    fun queue(context: Context): List<YouTubeVideo> = load(context, QUEUE)

    fun saveFavorites(context: Context, videos: List<YouTubeVideo>) =
        save(context, FAVORITES, videos.distinctBy { it.videoId })

    fun saveQueue(context: Context, videos: List<YouTubeVideo>) =
        save(context, QUEUE, videos.distinctBy { it.videoId }.take(30))

    fun addHistory(context: Context, video: YouTubeVideo): List<YouTubeVideo> {
        val updated = (listOf(video) + history(context))
            .distinctBy { it.videoId }
            .take(30)
        save(context, HISTORY, updated)
        return updated
    }

    fun encode(videos: List<YouTubeVideo>): String = JSONArray().apply {
        videos.forEach { video ->
            put(JSONObject().apply {
                put("videoId", video.videoId)
                put("title", video.title)
                put("channel", video.channel)
                put("thumbnailUrl", video.thumbnailUrl)
            })
        }
    }.toString()

    fun decode(value: String): List<YouTubeVideo> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val videoId = item.optString("videoId")
                if (videoId.isBlank()) continue
                add(
                    YouTubeVideo(
                        videoId = videoId,
                        title = item.optString("title"),
                        channel = item.optString("channel"),
                        thumbnailUrl = item.optString("thumbnailUrl")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun load(context: Context, key: String): List<YouTubeVideo> {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, "[]").orEmpty()
        return decode(value)
    }

    private fun save(context: Context, key: String, videos: List<YouTubeVideo>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, encode(videos))
            .apply()
    }
}

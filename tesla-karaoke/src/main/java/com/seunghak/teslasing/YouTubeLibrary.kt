package com.seunghak.teslasing

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object YouTubeLibrary {
    private const val PREFS_NAME = "tesla_sing_library"
    private const val FAVORITES = "favorites"
    private const val HISTORY = "history"
    private const val QUEUE = "queue"
    private const val PLAYBACK_VIDEO_ID = "playback_video_id"
    private const val PLAYBACK_POSITION_SECONDS = "playback_position_seconds"
    private const val SINGERS = "singers"
    private const val PLAY_COUNTS = "play_counts"
    const val DEFAULT_SINGER = "나"

    fun favorites(context: Context): List<YouTubeVideo> = load(context, FAVORITES)
    fun history(context: Context): List<YouTubeVideo> = load(context, HISTORY).take(30)
    fun mostPlayed(context: Context): List<YouTubeVideo> = load(context, HISTORY)
        .filter { it.playCount > 0 }
        .sortedByDescending { it.playCount }
        .take(20)
    fun queue(context: Context): List<YouTubeVideo> = load(context, QUEUE)

    fun saveFavorites(context: Context, videos: List<YouTubeVideo>) =
        save(context, FAVORITES, videos.distinctBy { it.videoId })

    fun saveQueue(context: Context, videos: List<YouTubeVideo>) =
        save(context, QUEUE, videos.distinctBy { it.videoId }.take(30))

    fun singers(context: Context): List<String> = runCatching {
        val values = JSONArray(preferences(context).getString(SINGERS, "[]").orEmpty())
        buildList {
            for (index in 0 until values.length()) {
                values.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrDefault(emptyList()).ifEmpty { listOf(DEFAULT_SINGER) }

    fun addSinger(context: Context, name: String): List<String> {
        val normalized = name.trim().take(10)
        if (normalized.isBlank()) return singers(context)
        val updated = (singers(context) + normalized).distinct().take(8)
        preferences(context).edit()
            .putString(SINGERS, JSONArray(updated).toString())
            .apply()
        return updated
    }

    fun removeSinger(context: Context, name: String): List<String> {
        if (name == DEFAULT_SINGER) return singers(context)
        val updated = singers(context).filterNot { it == name }.ifEmpty { listOf(DEFAULT_SINGER) }
        preferences(context).edit()
            .putString(SINGERS, JSONArray(updated).toString())
            .apply()
        return updated
    }

    fun fairQueue(videos: List<YouTubeVideo>): List<YouTubeVideo> {
        val groups = linkedMapOf<String, ArrayDeque<YouTubeVideo>>()
        videos.distinctBy { it.videoId }.forEach { video ->
            val singer = video.requester.ifBlank { DEFAULT_SINGER }
            groups.getOrPut(singer) { ArrayDeque() }.addLast(video.copy(requester = singer))
        }
        return buildList {
            while (groups.values.any { it.isNotEmpty() } && size < 30) {
                groups.values.forEach { songs ->
                    if (songs.isNotEmpty() && size < 30) add(songs.removeFirst())
                }
            }
        }
    }

    fun startPlayback(context: Context, videos: List<YouTubeVideo>) {
        val playlist = videos.distinctBy { it.videoId }.take(30)
        val preferences = preferences(context)
        val firstVideoId = playlist.firstOrNull()?.videoId.orEmpty()
        preferences.edit()
            .putString(QUEUE, encode(playlist))
            .apply {
                if (preferences.getString(PLAYBACK_VIDEO_ID, "") != firstVideoId) {
                    remove(PLAYBACK_VIDEO_ID)
                    remove(PLAYBACK_POSITION_SECONDS)
                }
            }
            .apply()
    }

    fun playbackPositionSeconds(context: Context, videoId: String): Float {
        val preferences = preferences(context)
        if (preferences.getString(PLAYBACK_VIDEO_ID, "") != videoId) return 0f
        return preferences.getFloat(PLAYBACK_POSITION_SECONDS, 0f).coerceAtLeast(0f)
    }

    fun savePlaybackPosition(context: Context, videoId: String, seconds: Float) {
        if (videoId.isBlank() || !seconds.isFinite() || seconds < 0f) return
        preferences(context).edit()
            .putString(PLAYBACK_VIDEO_ID, videoId)
            .putFloat(PLAYBACK_POSITION_SECONDS, seconds)
            .apply()
    }

    fun completePlayback(
        context: Context,
        completedVideoId: String,
        remainingVideos: List<YouTubeVideo>
    ) {
        val remaining = remainingVideos.distinctBy { it.videoId }.take(30)
        val preferences = preferences(context)
        preferences.edit()
            .putString(QUEUE, encode(remaining))
            .apply {
                if (preferences.getString(PLAYBACK_VIDEO_ID, "") == completedVideoId) {
                    remove(PLAYBACK_VIDEO_ID)
                    remove(PLAYBACK_POSITION_SECONDS)
                }
            }
            .apply()
    }

    fun addHistory(context: Context, video: YouTubeVideo): List<YouTubeVideo> {
        val preferences = preferences(context)
        val counts = runCatching {
            JSONObject(preferences.getString(PLAY_COUNTS, "{}").orEmpty())
        }.getOrDefault(JSONObject())
        val playCount = counts.optInt(video.videoId, 0) + 1
        counts.put(video.videoId, playCount)
        preferences.edit().putString(PLAY_COUNTS, counts.toString()).apply()

        val playedVideo = video.copy(requester = "", playCount = playCount)
        val updated = (listOf(playedVideo) + load(context, HISTORY))
            .distinctBy { it.videoId }
            .take(100)
        save(context, HISTORY, updated)
        return updated.take(30)
    }

    fun encode(videos: List<YouTubeVideo>): String = JSONArray().apply {
        videos.forEach { video ->
            put(JSONObject().apply {
                put("videoId", video.videoId)
                put("title", video.title)
                put("channel", video.channel)
                put("thumbnailUrl", video.thumbnailUrl)
                put("requester", video.requester)
                put("playCount", video.playCount)
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
                        thumbnailUrl = item.optString("thumbnailUrl"),
                        requester = item.optString("requester"),
                        playCount = item.optInt("playCount")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun load(context: Context, key: String): List<YouTubeVideo> {
        val value = preferences(context).getString(key, "[]").orEmpty()
        return decode(value)
    }

    private fun save(context: Context, key: String, videos: List<YouTubeVideo>) {
        preferences(context)
            .edit()
            .putString(key, encode(videos))
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

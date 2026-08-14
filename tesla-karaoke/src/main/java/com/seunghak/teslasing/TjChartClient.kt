package com.seunghak.teslasing

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

object TjChartClient {
    private const val CHART_API_URL = "https://www.tjmedia.com/legacy/api/topAndHot100"

    fun koreanTop100(): List<YouTubeVideo> {
        val endDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val startDate = endDate.minusDays(7)
        val requestBody = "chartType=TOP" +
            "&searchStartDate=$startDate" +
            "&searchEndDate=$endDate" +
            "&strType=1"

        val connection = URL(CHART_API_URL).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", "TeSing Android")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(requestBody) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) error("TJ 가요 TOP100을 불러오지 못했습니다. ($status)")

            val response = JSONObject(body)
            if (response.optString("resultCode") != "99") {
                error(response.optString("resultMsg", "TJ 차트 응답이 올바르지 않습니다."))
            }
            val items = response.getJSONObject("resultData").getJSONArray("items")
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val rank = item.optString("rank").toIntOrNull() ?: continue
                    val songNumber = item.optString("pro")
                    val title = item.optString("indexTitle").trim()
                    val artist = item.optString("indexSong").trim()
                    if (songNumber.isBlank() || title.isBlank() || artist.isBlank()) continue
                    add(
                        YouTubeVideo(
                            videoId = "tj-chart-$songNumber",
                            title = title,
                            channel = "$artist · TJ $songNumber",
                            thumbnailUrl = item.optString("imgthumb_path"),
                            lookupQuery = "$artist $title",
                            chartRank = rank
                        )
                    )
                }
            }.take(100).also {
                if (it.isEmpty()) error("TJ 가요 TOP100 목록 형식이 변경되었습니다.")
            }
        } finally {
            connection.disconnect()
        }
    }
}

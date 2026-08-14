package com.seunghak.teslasing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class YouTubePlayerActivity : Activity() {
    private var player: WebView? = null
    private var playlist: List<YouTubeVideo> = emptyList()
    private var currentIndex = 0
    private val handledPlaybackFailures = mutableSetOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFinishingPlayback = false
    private lateinit var titleLabel: TextView
    private lateinit var channelLabel: TextView
    private lateinit var nextSongLabel: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val fallbackVideo = YouTubeVideo(
            videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty(),
            title = intent.getStringExtra(EXTRA_VIDEO_TITLE).orEmpty(),
            channel = intent.getStringExtra(EXTRA_VIDEO_CHANNEL).orEmpty(),
            thumbnailUrl = ""
        )
        playlist = YouTubeLibrary.decode(intent.getStringExtra(EXTRA_PLAYLIST).orEmpty())
            .ifEmpty { listOf(fallbackVideo).filter { it.videoId.isNotBlank() } }
        val firstVideo = playlist.firstOrNull()
        if (firstVideo == null) {
            finish()
            return
        }
        val resumePositionSeconds = YouTubeLibrary.playbackPositionSeconds(this, firstVideo.videoId)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        root.addView(createHeader(firstVideo))

        val appIdUrl = "https://com.seunghak.teslasing"
        player = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(PlayerBridge(), "TeSing")
            loadDataWithBaseURL(
                "$appIdUrl/",
                playerHtml(firstVideo.videoId, appIdUrl, resumePositionSeconds),
                "text/html",
                "UTF-8",
                null
            )
        }
        root.addView(
            player,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        root.addView(createPlaybackControls())
        setContentView(root)
    }

    private fun createPlaybackControls(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(8))
            setBackgroundColor(Color.rgb(9, 11, 16))
        }
        val seekAndTempo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        seekAndTempo.addView(controlButton("−10초") { runPlayerCommand("seekBy(-10)") })
        seekAndTempo.addView(controlButton("+10초") { runPlayerCommand("seekBy(10)") })
        val tempoLabel = controlLabel("템포 1.0x")
        seekAndTempo.addView(tempoLabel)
        val rates = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        seekAndTempo.addView(
            SeekBar(this).apply {
                max = rates.lastIndex
                progress = 2
                setOnSeekBarChangeListener(simpleSeekListener { index ->
                    val rate = rates[index]
                    tempoLabel.text = "템포 ${rate}x"
                    runPlayerCommand("setAppRate($rate)")
                })
            },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        panel.addView(seekAndTempo)

        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val volumeLabel = controlLabel("노래 100")
        volumeRow.addView(volumeLabel)
        volumeRow.addView(
            SeekBar(this).apply {
                max = 100
                progress = 100
                setOnSeekBarChangeListener(simpleSeekListener { volume ->
                    volumeLabel.text = "노래 $volume"
                    runPlayerCommand("setAppVolume($volume)")
                })
            },
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )
        panel.addView(volumeRow)
        return panel
    }

    private fun controlButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun controlLabel(label: String): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 13f
        setPadding(dp(12), 0, dp(4), 0)
    }

    private fun simpleSeekListener(onChanged: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    private fun runPlayerCommand(command: String) {
        player?.evaluateJavascript(command, null)
    }

    private fun playerHtml(
        videoId: String,
        appIdUrl: String,
        resumePositionSeconds: Float
    ): String = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
          <meta name="referrer" content="strict-origin-when-cross-origin">
          <style>
            html, body, #player { width:100%; height:100%; margin:0; padding:0; background:#000; overflow:hidden; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player;
            var activeVideoId = '$videoId';
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width: '100%', height: '100%', videoId: '$videoId',
                playerVars: { autoplay: 1, playsinline: 1, fs: 1, rel: 0, origin: '$appIdUrl' },
                events: {
                  onReady: function(e) {
                    if ($resumePositionSeconds > 0) e.target.seekTo($resumePositionSeconds, true);
                    e.target.playVideo();
                    window.setInterval(reportPlaybackProgress, 2000);
                  },
                  onStateChange: function(e) {
                    if (e.data === YT.PlayerState.ENDED) TeSing.onVideoEnded();
                    if (e.data === YT.PlayerState.PAUSED) reportPlaybackProgress();
                  },
                  onError: function(e) {
                    var failedVideoId = activeVideoId;
                    if (player && player.getVideoData) {
                      var videoData = player.getVideoData();
                      if (videoData && videoData.video_id) failedVideoId = videoData.video_id;
                    }
                    TeSing.onVideoError(e.data, failedVideoId);
                  }
                }
              });
            }
            function setAppVolume(value) { if (player && player.setVolume) player.setVolume(value); }
            function setAppRate(value) { if (player && player.setPlaybackRate) player.setPlaybackRate(value); }
            function seekBy(delta) {
              if (player && player.getCurrentTime && player.seekTo) {
                player.seekTo(Math.max(0, player.getCurrentTime() + delta), true);
              }
            }
            function reportPlaybackProgress() {
              if (player && player.getCurrentTime) {
                TeSing.onPlaybackProgress(activeVideoId, player.getCurrentTime());
              }
            }
            function loadVideo(videoId) {
              activeVideoId = videoId;
              if (player && player.loadVideoById) player.loadVideoById(videoId);
            }
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun createHeader(video: YouTubeVideo): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(9, 11, 16))
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleLabel = TextView(this).apply {
            text = video.title
            setTextColor(Color.WHITE)
            textSize = 17f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        labels.addView(titleLabel)
        channelLabel = TextView(this).apply {
            text = queueStatus(video)
            setTextColor(Color.rgb(157, 163, 176))
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        labels.addView(channelLabel)
        nextSongLabel = TextView(this).apply {
            text = nextSongStatus()
            setTextColor(Color.rgb(53, 220, 148))
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        labels.addView(nextSongLabel)
        header.addView(labels, LinearLayout.LayoutParams(0, dp(72), 1f))
        header.addView(Button(this).apply {
            text = "YouTube 앱"
            setOnClickListener { currentVideo()?.let { openYouTubeApp(it.videoId) } }
        })
        header.addView(Button(this).apply {
            text = "닫기 ✕"
            setOnClickListener { finish() }
        })
        return header
    }

    private fun currentVideo(): YouTubeVideo? = playlist.getOrNull(currentIndex)

    private fun queueStatus(video: YouTubeVideo): String {
        val remaining = (playlist.size - currentIndex - 1).coerceAtLeast(0)
        val singer = video.requester.ifBlank { YouTubeLibrary.DEFAULT_SINGER }
        val status = "$singer · ${video.channel}"
        return if (remaining > 0) "$status · 다음 곡 ${remaining}개" else status
    }

    private fun nextSongStatus(): String {
        val next = playlist.getOrNull(currentIndex + 1) ?: return "다음 곡 없음"
        val singer = next.requester.ifBlank { YouTubeLibrary.DEFAULT_SINGER }
        return "다음 곡 · $singer · ${next.title}"
    }

    private fun playNext() {
        completeCurrentVideo()
        if (currentIndex + 1 >= playlist.size) return finishPlayback("마지막 곡의 재생이 끝났습니다.")
        currentIndex += 1
        val next = currentVideo() ?: return
        titleLabel.text = next.title
        channelLabel.text = queueStatus(next)
        nextSongLabel.text = nextSongStatus()
        YouTubeLibrary.addHistory(this, next)
        runPlayerCommand("loadVideo('${next.videoId}')")
    }

    private inner class PlayerBridge {
        @JavascriptInterface
        fun onVideoEnded() {
            runOnUiThread { playNext() }
        }

        @JavascriptInterface
        fun onVideoError(errorCode: Int, failedVideoId: String) {
            runOnUiThread { handlePlaybackFailure(errorCode, failedVideoId) }
        }

        @JavascriptInterface
        fun onPlaybackProgress(videoId: String, seconds: Double) {
            runOnUiThread {
                if (isFinishingPlayback || currentVideo()?.videoId != videoId) return@runOnUiThread
                YouTubeLibrary.savePlaybackPosition(
                    this@YouTubePlayerActivity,
                    videoId,
                    seconds.toFloat()
                )
            }
        }
    }

    private fun handlePlaybackFailure(errorCode: Int, failedVideoId: String) {
        val failedVideo = currentVideo() ?: return

        // 이전 영상에서 늦게 도착한 오류 콜백이 다음 곡까지 건너뛰지 않도록 막는다.
        if (failedVideoId.isNotBlank() && failedVideoId != failedVideo.videoId) return
        if (!handledPlaybackFailures.add(failedVideo.videoId)) return

        val reason = when (errorCode) {
            2 -> "잘못된 영상 주소"
            5 -> "이 기기에서 재생할 수 없는 영상"
            100 -> "삭제되었거나 비공개인 영상"
            101, 150 -> "앱 내 재생이 차단된 영상"
            153 -> "YouTube가 앱 정보를 확인하지 못한 영상"
            else -> "재생할 수 없는 영상 (오류 $errorCode)"
        }

        if (currentIndex + 1 < playlist.size) {
            Toast.makeText(this, "$reason · 다음 곡으로 넘어갑니다.", Toast.LENGTH_LONG).show()
            playNext()
        } else {
            completeCurrentVideo()
            finishPlayback("$reason · 예약된 다음 곡이 없습니다.")
        }
    }

    private fun completeCurrentVideo() {
        val completedVideoId = currentVideo()?.videoId ?: return
        YouTubeLibrary.completePlayback(
            this,
            completedVideoId,
            playlist.drop(currentIndex + 1)
        )
    }

    private fun finishPlayback(message: String) {
        if (isFinishingPlayback) return
        isFinishingPlayback = true
        runPlayerCommand("if (player && player.pauseVideo) player.pauseVideo()")
        titleLabel.text = message
        channelLabel.text = "잠시 후 재생 화면을 닫습니다."
        nextSongLabel.text = ""
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) finish()
        }, PLAYER_FINISH_DELAY_MS)
    }

    private fun openYouTubeApp(videoId: String) {
        val watchUrl = Uri.parse("https://www.youtube.com/watch?v=$videoId")
        val appIntent = Intent(Intent.ACTION_VIEW, watchUrl).apply {
            setPackage("com.google.android.youtube")
        }
        val fallback = Intent(Intent.ACTION_VIEW, watchUrl)
        startActivity(if (appIntent.resolveActivity(packageManager) != null) appIntent else fallback)
    }

    override fun onPause() {
        runPlayerCommand("reportPlaybackProgress()")
        player?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        player?.onResume()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        player?.stopLoading()
        player = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_VIDEO_ID = "youtube_video_id"
        const val EXTRA_VIDEO_TITLE = "youtube_video_title"
        const val EXTRA_VIDEO_CHANNEL = "youtube_video_channel"
        const val EXTRA_PLAYLIST = "youtube_playlist"
        private const val PLAYER_FINISH_DELAY_MS = 2_000L
    }
}

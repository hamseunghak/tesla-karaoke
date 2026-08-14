package com.seunghak.teslasing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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

class YouTubePlayerActivity : Activity() {
    private var player: WebView? = null
    private var playlist: List<YouTubeVideo> = emptyList()
    private var currentIndex = 0
    private lateinit var titleLabel: TextView
    private lateinit var channelLabel: TextView

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
                playerHtml(firstVideo.videoId, appIdUrl),
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

    private fun playerHtml(videoId: String, appIdUrl: String): String = """
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
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width: '100%', height: '100%', videoId: '$videoId',
                playerVars: { autoplay: 1, playsinline: 1, fs: 1, rel: 0, origin: '$appIdUrl' },
                events: {
                  onReady: function(e) { e.target.playVideo(); },
                  onStateChange: function(e) {
                    if (e.data === YT.PlayerState.ENDED) TeSing.onVideoEnded();
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
            function loadVideo(videoId) {
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
        }
        labels.addView(titleLabel)
        channelLabel = TextView(this).apply {
            text = queueStatus(video)
            setTextColor(Color.rgb(157, 163, 176))
            textSize = 11f
            maxLines = 1
        }
        labels.addView(channelLabel)
        header.addView(labels, LinearLayout.LayoutParams(0, dp(56), 1f))
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
        return if (remaining > 0) "${video.channel} · 다음 곡 ${remaining}개" else video.channel
    }

    private fun playNext() {
        if (currentIndex + 1 >= playlist.size) return
        currentIndex += 1
        val next = currentVideo() ?: return
        titleLabel.text = next.title
        channelLabel.text = queueStatus(next)
        YouTubeLibrary.addHistory(this, next)
        runPlayerCommand("loadVideo('${next.videoId}')")
    }

    private inner class PlayerBridge {
        @JavascriptInterface
        fun onVideoEnded() {
            runOnUiThread { playNext() }
        }
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
        player?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        player?.onResume()
    }

    override fun onDestroy() {
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
    }
}

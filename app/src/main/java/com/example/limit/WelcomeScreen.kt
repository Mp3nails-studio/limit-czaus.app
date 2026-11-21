package com.example.limit

import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Filmik powitalny (res/raw/welcome.mp4) na pełnym ekranie, pokazywany tylko przy pierwszym uruchomieniu. */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val ctx = LocalContext.current

    // Tryb pełnoekranowy na czas filmiku.
    DisposableEffect(Unit) {
        val window = (ctx as? ComponentActivity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                var done = false
                fun finish() {
                    if (!done) {
                        done = true
                        onFinished()
                    }
                }

                FrameLayout(c).apply {
                    keepScreenOn = true
                    val video = VideoView(c).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                        )
                        setVideoURI(Uri.parse("android.resource://${c.packageName}/${R.raw.welcome}"))
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            start()
                        }
                        setOnCompletionListener { finish() }
                        // Gdyby filmik się nie odtworzył, od razu przejdź do aplikacji.
                        setOnErrorListener { _, _, _ ->
                            finish()
                            true
                        }
                    }
                    addView(video)
                }
            },
            onRelease = { root -> (root.getChildAt(0) as? VideoView)?.stopPlayback() }
        )

        FilledTonalButton(
            onClick = onFinished,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(16.dp)
        ) { Text("Pomiń") }
    }
}

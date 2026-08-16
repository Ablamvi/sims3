package com.zernex.sim3d

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Démo 3D Sims-like polie
 * - Glisser = orbit caméra
 * - Le Sim marche tout seul vers la chaise et s'assoit (boucle)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: SimRenderer

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        renderer = SimRenderer()
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        var lastX = 0f
        var lastY = 0f
        glView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x
                    lastY = e.y
                }
                MotionEvent.ACTION_MOVE -> {
                    renderer.orbitYaw += (e.x - lastX) * 0.4f
                    renderer.orbitPitch = (renderer.orbitPitch + (e.y - lastY) * 0.3f).coerceIn(-15f, 55f)
                    lastX = e.x
                    lastY = e.y
                }
            }
            true
        }

        val hint = TextView(this).apply {
            text = "  Glisse = caméra  ·  Le Sim marche et s'assoit tout seul"
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 13f
            setPadding(16, 28, 16, 16)
            setBackgroundColor(0x66000000)
        }

        setContentView(FrameLayout(this).apply {
            addView(glView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }
}

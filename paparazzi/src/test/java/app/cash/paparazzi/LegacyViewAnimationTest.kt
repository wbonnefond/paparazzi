/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.paparazzi

import android.graphics.Color
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.image.BufferedImage

/**
 * A legacy [android.view.animation.Animation] reads the view tree's drawing time rather than an
 * animator clock, so it advances only if each frame draws at that frame's time.
 */
@RunWith(Parameterized::class)
class LegacyViewAnimationTest(renderingMode: RenderingMode) {
  private val frames = mutableListOf<BufferedImage>()

  @get:Rule
  val paparazzi = Paparazzi(renderingMode = renderingMode, snapshotHandler = FrameCollector(frames))

  @Test
  fun translateAnimationAdvancesWithGifFrames() {
    val animated = object : FrameLayout(paparazzi.context) {
      override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation(
          TranslateAnimation(0f, 0f, 0f, 100f).apply {
            duration = 1_000L
            interpolator = LinearInterpolator()
          }
        )
      }
    }
    animated.setPadding(0, 0, 0, 200)
    animated.addView(View(paparazzi.context).apply { setBackgroundColor(Color.RED) }, 100, 100)

    paparazzi.gif(animated, start = 0L, end = 1_000L, fps = 4)

    // NORMAL scales its output to fit the device, so assert the motion rather than exact offsets.
    val tops = frames.map { it.topOfRed() }
    assertThat(tops).hasSize(5)
    assertThat(tops.first()).isEqualTo(0)
    assertThat(tops).isInStrictOrder()
  }

  private fun BufferedImage.topOfRed(): Int = (0 until height).first { y -> getRGB(0, y) and 0xFFFFFF == 0xFF0000 }

  private class FrameCollector(private val frames: MutableList<BufferedImage>) : SnapshotHandler {
    override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) =
      object : SnapshotHandler.FrameHandler {
        override fun handle(image: BufferedImage) {
          frames += image
        }

        override fun close() = Unit
      }

    override fun close() = Unit
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): List<RenderingMode> = listOf(RenderingMode.NORMAL, RenderingMode.SHRINK)
  }
}

package io.github.afanasievn.devicerisksignals.active.example

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.afanasievn.devicerisksignals.active.DeviceRiskActiveProbes
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Native consumer of the optional active component: only its public API and system Android classes
 * are available here. Collection opens a loopback socket, so every call runs on a worker thread;
 * the passive core's example stays socket-free by design.
 */
class MainActivity : Activity() {
  private val worker = Executors.newSingleThreadExecutor()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val activeProbes = DeviceRiskActiveProbes()
    val output = TextView(this).apply { setTextIsSelectable(true) }
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(24, 24, 24, 24)
      addView(TextView(this@MainActivity).apply {
        text = "Active probe example. Collection connects to 127.0.0.1 only, on a worker thread, " +
          "and results stay on this screen. A false flag can mean nothing listens or that the " +
          "attempt could not complete."
      })
    }
    content.addView(Button(this).apply {
      text = "Frida scan (active, loopback)"
      setOnClickListener {
        isEnabled = false
        output.text = "Collecting..."
        worker.execute {
          val result = try {
            JSONObject(activeProbes.collectFridaScan().toRawMap()).toString(2)
          } catch (error: Exception) {
            "Collection failed: ${error.javaClass.simpleName}"
          }
          runOnUiThread {
            if (!isDestroyed) {
              output.text = result
              isEnabled = true
            }
          }
        }
      }
    })
    content.addView(output)
    setContentView(ScrollView(this).apply { addView(content) })
  }

  override fun onDestroy() {
    // Stops queued work only; a socket read already in progress ends on its own timeout.
    worker.shutdownNow()
    super.onDestroy()
  }
}

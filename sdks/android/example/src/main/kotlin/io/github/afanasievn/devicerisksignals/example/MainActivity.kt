package io.github.afanasievn.devicerisksignals.example

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.afanasievn.devicerisksignals.DeviceRiskSignals
import org.json.JSONObject

/** Native consumer: only the SDK public API and system Android classes are available here. */
class MainActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val signals = DeviceRiskSignals(applicationContext)
    val output = TextView(this).apply { setTextIsSelectable(true) }
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(24, 24, 24, 24)
      addView(TextView(this@MainActivity).apply {
        text = "Local SDK example. Choose a collection; results stay on this screen."
      })
    }
    fun action(label: String, collect: () -> Map<String, Any>) {
      content.addView(Button(this).apply {
        text = label
        setOnClickListener {
          output.text = try {
            JSONObject(collect()).toString(2)
          } catch (error: Exception) {
            "Collection failed: ${error.javaClass.simpleName}"
          }
        }
      })
    }
    action("Device identity") { signals.collectDeviceIdentity().toRawMap() }
    action("Locale") { signals.collectLocale().toRawMap() }
    action("Application") { signals.collectApplication().toRawMap() }
    action("Hardware") { signals.collectHardware().toRawMap() }
    action("Fonts (optional)") { signals.collectFonts().toRawMap() }
    action("Runtime timing (optional)") { signals.collectRuntimeTiming().toRawMap() }
    action("Numeric consistency (optional)") { signals.collectNumericConsistency().toRawMap() }
    action("Audio latency (optional)") { signals.collectAudioLatency().toRawMap() }
    content.addView(output)
    setContentView(ScrollView(this).apply { addView(content) })
  }
}

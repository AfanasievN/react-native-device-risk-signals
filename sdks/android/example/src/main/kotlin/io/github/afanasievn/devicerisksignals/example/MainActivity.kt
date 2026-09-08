package io.github.afanasievn.devicerisksignals.example

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.afanasievn.devicerisksignals.DeviceRiskSignals
import io.github.afanasievn.devicerisksignals.TransactionObservationSession
import org.json.JSONObject
import java.util.concurrent.Executors

/** Native consumer: only the SDK public API and system Android classes are available here. */
class MainActivity : Activity() {
  private var transactionSession: TransactionObservationSession? = null
  private val gpuWorker = Executors.newSingleThreadExecutor()
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
    action("OS integrity (passive)") { signals.collectOsIntegrity().toRawMap() }
    action("Network (local observations)") { signals.collectNetwork().toRawMap() }
    action("Telephony") { signals.collectTelephony().toRawMap() }
    action("Cached location") { signals.collectGeolocation().toRawMap() }
    action("Media / finite app audit (optional)") { signals.collectMediaBluetoothApps().toRawMap() }
    action("Device security posture") { signals.collectDeviceSecurityPosture().toRawMap() }
    action("Transaction snapshot (optional)") { signals.collectTransactionSafety().toRawMap() }
    action("Start transaction observation (optional)") {
      val session = transactionSession ?: signals.createTransactionObservationSession().also {
        transactionSession = it
      }
      session.attach(this)
      session.snapshot()?.toRawMap().orEmpty()
    }
    action("Read transaction observation") { transactionSession?.snapshot()?.toRawMap().orEmpty() }
    action("Stop transaction observation") {
      transactionSession?.close()
      transactionSession = null
      emptyMap()
    }
    action("Runtime timing (optional)") { signals.collectRuntimeTiming().toRawMap() }
    action("Numeric consistency (optional)") { signals.collectNumericConsistency().toRawMap() }
    action("Audio latency (optional)") { signals.collectAudioLatency().toRawMap() }
    content.addView(Button(this).apply {
      text = "GPU benchmark (optional, worker thread)"
      setOnClickListener {
        isEnabled = false
        gpuWorker.execute {
          val result = try {
            JSONObject(signals.collectGpuBenchmark().toRawMap()).toString(2)
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

  override fun onStop() {
    transactionSession?.detach()
    super.onStop()
  }

  override fun onDestroy() {
    gpuWorker.shutdownNow() // Does not forcibly cancel a GPU driver call already running.
    transactionSession?.close()
    transactionSession = null
    super.onDestroy()
  }
}

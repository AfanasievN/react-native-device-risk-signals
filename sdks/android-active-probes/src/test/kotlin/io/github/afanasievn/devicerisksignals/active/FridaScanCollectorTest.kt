package io.github.afanasievn.devicerisksignals.active

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Real-socket JVM tests. Every server binds 127.0.0.1 on port 0 (ephemeral) and is closed in
 * `finally`; the collector is pointed at that port through its seam, so nothing here touches the
 * production port 27042 and nothing requires network access.
 */
class FridaScanCollectorTest {

  @Test fun rejectHandshakeOnAnOpenPortIsReportedAsOpenAndRejecting() {
    val server = serve { socket ->
      readAuth(socket)
      socket.getOutputStream().write("REJECT [\"plain\"]\r\n".toByteArray(Charsets.US_ASCII))
      socket.getOutputStream().flush()
    }
    try {
      val signals = collectAgainst(server.localPort)
      assertEquals(
        mapOf(
          "scanPerformed" to true,
          "scannedPort" to server.localPort,
          "defaultPortOpen" to true,
          "fridaHandshakeReject" to true,
        ),
        signals.toRawMap(),
      )
    } finally {
      server.close()
    }
  }

  @Test fun nonRejectReplyIsReportedAsOpenWithoutRejection() {
    val server = serve { socket ->
      readAuth(socket)
      socket.getOutputStream().write("OK\r\n".toByteArray(Charsets.US_ASCII))
      socket.getOutputStream().flush()
    }
    try {
      val signals = collectAgainst(server.localPort)
      assertEquals(true, signals.defaultPortOpen)
      assertEquals(false, signals.fridaHandshakeReject)
    } finally {
      server.close()
    }
  }

  @Test fun silentListenerIsReportedAsOpenWithoutRejectionAndReturnsAfterTheReadTimeout() {
    val release = CountDownLatch(1)
    val server = serve { socket ->
      readAuth(socket)
      // Hold the connection open without answering so the collector's read timeout is what ends
      // the call. Released by the test in `finally`.
      release.await(5, TimeUnit.SECONDS)
    }
    try {
      val signals = collectAgainst(server.localPort)
      assertNotNull(signals)
      assertEquals(true, signals.defaultPortOpen)
      assertEquals(false, signals.fridaHandshakeReject)
    } finally {
      release.countDown()
      server.close()
    }
  }

  @Test fun immediateCloseAfterAcceptIsReportedAsOpenWithoutRejectionAndThrowsNothing() {
    val server = serve { /* accept and let `use` close the connection at once */ }
    try {
      val signals = collectAgainst(server.localPort)
      assertEquals(true, signals.defaultPortOpen)
      assertEquals(false, signals.fridaHandshakeReject)
    } finally {
      server.close()
    }
  }

  @Test fun closedPortIsReportedAsNotOpenWhileTheScanIsStillReported() {
    val probe = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST))
    val port = probe.localPort
    probe.close()

    val signals = collectAgainst(port)

    assertEquals(
      mapOf(
        "scanPerformed" to true,
        "scannedPort" to port,
        "defaultPortOpen" to false,
        "fridaHandshakeReject" to false,
      ),
      signals.toRawMap(),
    )
  }

  private fun collectAgainst(port: Int): FridaScanSignals =
    FridaScanCollector(host = LOOPBACK_HOST, port = port, timeoutMs = TEST_TIMEOUT_MS).collect()

  /** The collector writes one 0x00 byte then `AUTH\r\n`; drain that before answering. */
  private fun readAuth(socket: Socket) {
    socket.soTimeout = SERVER_READ_TIMEOUT_MS
    socket.getInputStream().read(ByteArray(AUTH_REQUEST_BYTES))
  }

  private fun serve(handler: (Socket) -> Unit): ServerSocket {
    val server = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST))
    val thread = Thread {
      try {
        server.accept().use { handler(it) }
      } catch (e: Exception) {
        // The test closes the server in `finally`; a pending accept or a reset connection then
        // fails by design and must not fail the test.
      }
    }
    thread.isDaemon = true
    thread.start()
    return server
  }

  private companion object {
    const val LOOPBACK_HOST = "127.0.0.1"
    const val TEST_TIMEOUT_MS = 300
    const val SERVER_READ_TIMEOUT_MS = 2_000
    const val AUTH_REQUEST_BYTES = 7
  }
}

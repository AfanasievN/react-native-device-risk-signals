package io.github.afanasievn.devicerisksignals.active

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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


  // --- Reassembly across reads -------------------------------------------------------------
  //
  // The handshake reply is a TCP stream, not a message. A listener is free to flush `REJECT` one
  // byte at a time, and the old collector's single `read` then saw one byte and answered "no
  // REJECT". These tests pin reassembly.

  @Test fun aRejectReplyDribbledOutOneByteAtATimeIsReassembledAcrossReads() {
    val server = serve { socket ->
      readAuth(socket)
      val out = socket.getOutputStream()
      for (byte in "REJECT [\"plain\"]\r\n".toByteArray(Charsets.US_ASCII)) {
        out.write(byte.toInt())
        out.flush()
        Thread.sleep(DRIBBLE_GAP_MS)
      }
    }
    try {
      val signals = collectAgainst(server.localPort, readTimeoutMs = GENEROUS_READ_MS)
      assertEquals(true, signals.defaultPortOpen)
      assertEquals(true, signals.fridaHandshakeReject)
    } finally {
      server.close()
    }
  }

  @Test fun aNonRejectReplyDribbledOutOneByteAtATimeStillReadsAsNoRejection() {
    val server = serve { socket ->
      readAuth(socket)
      val out = socket.getOutputStream()
      for (byte in "REJOICE\r\n".toByteArray(Charsets.US_ASCII)) {
        out.write(byte.toInt())
        out.flush()
        Thread.sleep(DRIBBLE_GAP_MS)
      }
    }
    try {
      val signals = collectAgainst(server.localPort, readTimeoutMs = GENEROUS_READ_MS)
      assertEquals(true, signals.defaultPortOpen)
      assertEquals(false, signals.fridaHandshakeReject)
    } finally {
      server.close()
    }
  }

  @Test fun aReplyTruncatedByEofBeforeThePrefixIsDecidedReadsAsNoRejection() {
    val server = serve { socket ->
      readAuth(socket)
      socket.getOutputStream().write("REJ".toByteArray(Charsets.US_ASCII))
      socket.getOutputStream().flush()
      // `use` in `serve` closes the connection here, so the collector sees EOF mid-prefix.
    }
    try {
      val signals = collectAgainst(server.localPort, readTimeoutMs = GENEROUS_READ_MS)
      assertEquals(true, signals.defaultPortOpen)
      assertEquals(false, signals.fridaHandshakeReject)
    } finally {
      server.close()
    }
  }

  // --- Separate connect and read budgets ---------------------------------------------------
  //
  // One shared value could not express "connect quickly, but wait longer for the answer", nor
  // "wait a long time for the connect, but give up on a silent listener quickly".

  @Test fun aReplyThatArrivesLongAfterTheConnectBudgetIsStillReadWithinTheReadBudget() {
    val server = serve { socket ->
      readAuth(socket)
      Thread.sleep(SLOW_REPLY_DELAY_MS)
      socket.getOutputStream().write("REJECT [\"plain\"]\r\n".toByteArray(Charsets.US_ASCII))
      socket.getOutputStream().flush()
    }
    try {
      val signals =
        collectAgainst(
          server.localPort,
          connectTimeoutMs = SHORT_CONNECT_MS,
          readTimeoutMs = GENEROUS_READ_MS,
        )
      assertEquals(true, signals.defaultPortOpen)
      assertEquals(true, signals.fridaHandshakeReject)
    } finally {
      server.close()
    }
  }

  @Test fun aSilentListenerEndsTheCallOnTheReadBudgetEvenWhenTheConnectBudgetIsLong() {
    val release = CountDownLatch(1)
    val server = serve { socket ->
      readAuth(socket)
      release.await(10, TimeUnit.SECONDS)
    }
    try {
      val startedAt = System.nanoTime()
      val signals =
        collectAgainst(
          server.localPort,
          connectTimeoutMs = GENEROUS_CONNECT_MS,
          readTimeoutMs = SHORT_READ_MS,
        )
      val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

      assertEquals(true, signals.defaultPortOpen)
      assertEquals(false, signals.fridaHandshakeReject)
      // The read budget, not the connect budget, is what ends a silent listener.
      assertTrue(
        "expected the call to end on the read budget, took ${elapsedMs}ms",
        elapsedMs < GENEROUS_CONNECT_MS,
      )
    } finally {
      release.countDown()
      server.close()
    }
  }

  @Test fun aConnectThatNeverCompletesEndsOnTheConnectBudgetAndNeverSpendsTheReadBudget() {
    // A backlog of one that is never accepted: the first connection fills the queue and every
    // later connect stalls, which is the only way to exercise a slow connect over loopback.
    val server = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST))
    val fillers = mutableListOf<Socket>()
    try {
      repeat(BACKLOG_FILLERS) {
        try {
          fillers += Socket().apply {
            connect(InetSocketAddress(LOOPBACK_HOST, server.localPort), SHORT_CONNECT_MS)
          }
        } catch (e: Exception) {
          // The queue is already full, which is exactly the state this test wants.
        }
      }

      val startedAt = System.nanoTime()
      val signals =
        collectAgainst(
          server.localPort,
          connectTimeoutMs = SHORT_CONNECT_MS,
          readTimeoutMs = GENEROUS_READ_MS,
        )
      val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

      assertEquals(false, signals.defaultPortOpen)
      assertEquals(false, signals.fridaHandshakeReject)
      // A failed connect must not go on to spend the read budget as well.
      assertTrue(
        "expected the call to end on the connect budget, took ${elapsedMs}ms",
        elapsedMs < GENEROUS_READ_MS,
      )
    } finally {
      fillers.forEach { runCatching { it.close() } }
      server.close()
    }
  }

  @Test fun theProductionDefaultsKeepTheWorstCaseBounded() {
    assertEquals(700, FridaScanCollector.CONNECT_TIMEOUT_MS)
    assertEquals(800, FridaScanCollector.READ_TIMEOUT_MS)
    assertEquals(
      FridaScanCollector.CONNECT_TIMEOUT_MS + FridaScanCollector.READ_TIMEOUT_MS,
      FridaScanCollector.WORST_CASE_TIMEOUT_MS,
    )
  }

  private fun collectAgainst(
    port: Int,
    connectTimeoutMs: Int = TEST_CONNECT_TIMEOUT_MS,
    readTimeoutMs: Int = TEST_READ_TIMEOUT_MS,
  ): FridaScanSignals =
    FridaScanCollector(
      host = LOOPBACK_HOST,
      port = port,
      connectTimeoutMs = connectTimeoutMs,
      readTimeoutMs = readTimeoutMs,
    ).collect()

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
    const val TEST_CONNECT_TIMEOUT_MS = 300
    const val TEST_READ_TIMEOUT_MS = 300
    const val SERVER_READ_TIMEOUT_MS = 2_000
    const val AUTH_REQUEST_BYTES = 7

    /** Deliberately asymmetric so a test can tell which budget ended the call. */
    const val SHORT_CONNECT_MS = 250
    const val GENEROUS_CONNECT_MS = 4_000
    const val SHORT_READ_MS = 250
    const val GENEROUS_READ_MS = 4_000

    const val DRIBBLE_GAP_MS = 25L
    const val SLOW_REPLY_DELAY_MS = 900L
    const val BACKLOG_FILLERS = 6
  }
}

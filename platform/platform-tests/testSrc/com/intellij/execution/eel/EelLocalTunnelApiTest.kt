// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.tests.eelHelpers.EelHelper
import com.intellij.platform.tests.eelHelpers.network.NetworkConstants
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
class EelLocalTunnelApiTest {
  companion object {

    private lateinit var executor: JavaMainClassExecutor

    @BeforeAll
    @JvmStatic
    fun createExecutor() {
      executor = JavaMainClassExecutor(EelHelper::class.java, "network")
    }
  }

  @Test
  fun testCheckFailureConnection(): Unit = timeoutRunBlocking(5.minutes) {
    when (localEel.tunnels.getConnectionToRemotePort(
      EelTunnelsApi.HostAddress.Builder(22U).hostname("google.com").connectionTimeout(5.seconds).build())) {
      is EelResult.Error -> Unit
      is EelResult.Ok -> Assertions.fail("Connection should fail")
    }
  }

  @Test
  fun testClientSuccessConnection(): Unit = timeoutRunBlocking(1.minutes) {
    val helper = localEel.exec.execute(executor.createBuilderToExecuteMain().build()).getOrThrow()
    try {
      val port = helper.stdout.consumeAsInputStream().bufferedReader().readLine().trim().toInt()
      val address = EelTunnelsApi
        .HostAddress.Builder(port.toUShort())
        .preferIPv4()
        .build()
      val connection = localEel.tunnels.getConnectionToRemotePort(address).getOrThrow()
      val buffer = ByteBuffer.allocate(4096)
      connection.receiveChannel.receive(buffer).getOrThrow()
      Assertions.assertEquals(NetworkConstants.HELLO_FROM_SERVER, NetworkConstants.fromByteBuffer(buffer.flip()))
      connection.sendChannel.sendWholeBuffer(NetworkConstants.HELLO_FROM_CLIENT.toBuffer()).getOrThrow()
      //      Helper closes the stream, so does the channel
      Assertions.assertEquals(ReadResult.EOF, connection.receiveChannel.receive(ByteBuffer.allocate(1)).getOrThrow())
    }
    finally {
      helper.kill()
    }
  }
}
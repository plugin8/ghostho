package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.pip.PipPackageManager
import com.example.engine.LogType
import com.example.engine.PyInterpreter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PyHost", appName)
  }

  @Test
  fun `test import detector detects telegram and requests`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pipManager = PipPackageManager(context)
    val script = """
      import telebot
      import requests
      import os
      import sys
      from flask import Flask
      from telegram.ext import Application
    """.trimIndent()

    val deps = pipManager.detectImports(script)
    assertTrue(deps.contains("pyTelegramBotAPI"))
    assertTrue(deps.contains("requests"))
    assertTrue(deps.contains("Flask") || deps.contains("flask"))
    assertTrue(!deps.contains("os"))
    assertTrue(!deps.contains("sys"))
  }

  @Test
  fun `test PyInterpreter executes statements and print`() = runBlocking {
    val logs = mutableListOf<String>()
    val interpreter = PyInterpreter(
      logCallback = { msg, type ->
        if (type == LogType.STDOUT) logs.add(msg)
      },
      onBotAuth = { _, _ -> },
      onUpdateProcessed = {}
    )

    val testScript = """
      x = 10
      y = 20
      print("Testing PyHost")
      print("Result: 30")
    """.trimIndent()

    interpreter.execute(testScript)
    assertTrue(logs.contains("Testing PyHost"))
    assertTrue(logs.contains("Result: 30"))
  }
}

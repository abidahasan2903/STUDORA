package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context and verify local storage`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("STUDORA", appName)

    val storage = com.example.core.LocalStorageManager(context)
    org.junit.Assert.assertTrue(storage.documentsDir.absolutePath.startsWith(context.filesDir.absolutePath))
    org.junit.Assert.assertTrue(storage.audioRecordingsDir.exists())
  }
}

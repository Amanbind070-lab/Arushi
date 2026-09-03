package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.DeviceActionBridge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Arushi AI", appName)
  }

  @Test
  fun `test safe device action validation for invalid app`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = DeviceActionBridge()
    val args = JSONObject().apply { put("appName", "unauthorized_malicious_app") }
    val result = bridge.executeAction(context, "openApp", args)

    assertFalse(result.success)
    assertEquals("openApp", result.actionName)
  }

  @Test
  fun `test url validator rejects non-http links`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = DeviceActionBridge()
    val args = JSONObject().apply { put("url", "javascript:alert(1)") }
    val result = bridge.executeAction(context, "openUrl", args)

    assertFalse(result.success)
  }

  @Test
  fun `test phone number dialing format`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val bridge = DeviceActionBridge()
    val args = JSONObject().apply { put("phoneNumber", "9876543210") }
    val result = bridge.executeAction(context, "makeCall", args)

    assertNotNull(result)
  }
}

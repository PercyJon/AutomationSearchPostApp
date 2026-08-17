package com.example.douyinautomation

import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.douyinautomation.automation.DouyinAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side manifest smoke checks that do not open, close, or otherwise interrupt the UI. */
@RunWith(AndroidJUnit4::class)
class AutomationAppIntegrationTest {
    @Test
    fun launcherActivityAndAccessibilityServiceAreDeclaredForAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA,
        )
        assertTrue(activityInfo.exported)

        val component = ComponentName(context, DouyinAccessibilityService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, serviceInfo.permission)
        assertTrue(serviceInfo.metaData?.containsKey("android.accessibilityservice") == true)
    }

    @Test
    fun reportsEnabledAccessibilityServiceIdsForDiagnostics() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val ids = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
            .joinToString { it.id }
        println("ENABLED_ACCESSIBILITY_SERVICE_COUNT=${ids.split(',').count { it.isNotBlank() }}")
        println("ENABLED_ACCESSIBILITY_SERVICE_IDS=$ids")
    }
}

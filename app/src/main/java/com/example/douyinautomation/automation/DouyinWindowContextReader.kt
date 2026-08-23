package com.example.douyinautomation.automation

import android.accessibilityservice.AccessibilityService

/**
 * Reads one immutable target-app window snapshot without performing navigation or UI actions.
 *
 * An app-owned overlay can briefly become the active accessibility window on some devices. In
 * that case, this reader preserves the existing fallback to a visible target-app window.
 */
class DouyinWindowContextReader(
    private val service: AccessibilityService,
    private val inspector: NodeTreeInspector,
) {
    @Suppress("DEPRECATION")
    fun read(): ScreenContext? {
        service.rootInActiveWindow?.let { activeRoot ->
            try {
                if (activeRoot.packageName?.toString() == TargetAppLauncher.DOUYIN_PACKAGE) {
                    return inspector.inspect(activeRoot)
                }
            } finally {
                activeRoot.recycle()
            }
        }

        return service.windows.orEmpty().firstNotNullOfOrNull { window ->
            val root = window.root
            try {
                if (root?.packageName?.toString() == TargetAppLauncher.DOUYIN_PACKAGE) {
                    inspector.inspect(root)
                } else {
                    null
                }
            } finally {
                root?.recycle()
                window.recycle()
            }
        }
    }
}

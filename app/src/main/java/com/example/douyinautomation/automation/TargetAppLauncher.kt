package com.example.douyinautomation.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * A deliberately narrow target-app launcher. M0 is scoped to the installed Douyin client only;
 * it does not enumerate or interact with unrelated apps.
 */
object TargetAppLauncher {
    const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(DOUYIN_PACKAGE, 0)
    }.isSuccess

    fun launch(context: Context): LaunchResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(DOUYIN_PACKAGE)
            ?: return LaunchResult.Failed("Douyin is not installed or has no launcher activity")

        return runCatching {
            // When a task starts from CURRENT_PROFILE, the operator has already prepared a
            // profile in Douyin.  CLEAR_TOP would destroy that navigation stack and restore the
            // launcher/splash surface, forcing the controller to wait for a profile that can no
            // longer be reached.  NEW_TASK lets Android bring the existing Douyin task forward
            // while preserving its current top activity.  This is also safer for the normal
            // search flow: the controller still waits for a detected page before acting.
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            LaunchResult.Started
        }.getOrElse { error ->
            LaunchResult.Failed(error.message ?: "Could not launch Douyin")
        }
    }
}

sealed interface LaunchResult {
    data object Started : LaunchResult
    data class Failed(val reason: String) : LaunchResult
}

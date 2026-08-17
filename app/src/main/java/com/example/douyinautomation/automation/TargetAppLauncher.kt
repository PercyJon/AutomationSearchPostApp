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
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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

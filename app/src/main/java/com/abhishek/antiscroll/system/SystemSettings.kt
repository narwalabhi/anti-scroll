package com.abhishek.antiscroll.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** The system screens where the user grants what [SystemChecks] reads. */
object SystemSettings {

    fun accessibility() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun overlay(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun usageAccess() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun notifications(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    // Asking directly needs a permission Play restricts; revisit before a Play Store release.
    @SuppressLint("BatteryLife")
    fun battery(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}

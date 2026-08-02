package com.indidevs.android.adbeesh.internal.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.indidevs.android.adbeesh.notifications.NotificationConfig
import com.indidevs.android.adbeesh.notifications.NotificationFlowConfig
import com.indidevs.android.adbeesh.pairing.AdbPairingUpdate

/**
 * Responsible for rendering system notifications.
 */
internal class NotificationView(
    private val context: Context,
    private val config: NotificationFlowConfig,
    private val sessionId: String,
    private val requestCodeBase: Int
) {
    companion object {
        private const val REQ_SETTINGS_INITIAL = 1
        private const val REQ_SETTINGS_AWAITING = 2
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                config.notificationChannelId,
                config.notificationChannelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun update(port: Int? = null, error: String? = null) =
        AdbPairingUpdate(sessionId, port, error)

    fun createSearchingNotification() = render(config.searching(update()), REQ_SETTINGS_INITIAL)

    fun createAwaitingCodeNotification(port: Int, action: NotificationCompat.Action? = null) =
        render(config.awaitingCode(update(port = port)), REQ_SETTINGS_AWAITING, action)

    fun createCodeSubmittedNotification() = render(config.codeSubmitted(update())).apply {
        setProgress(0, 0, true)
    }

    fun createPairingNotification() = render(config.pairing(update())).apply {
        setProgress(0, 0, true)
    }

    fun createSuccessNotification() = render(config.success(update())).apply {
        setOngoing(false)
    }

    fun createErrorNotification(error: String) = render(config.error(update(error = error))).apply {
        setOngoing(false)
    }

    private fun render(
        ui: NotificationConfig,
        settingsReqCode: Int = REQ_SETTINGS_INITIAL,
        action: NotificationCompat.Action? = null
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, config.notificationChannelId)
            .setSmallIcon(ui.smallIconRes)
            .setContentTitle(ui.title)
            .setContentText(ui.text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        ui.bigText?.let {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }

        // Action button
        action?.let {
            builder.addAction(it)
        }

        // Open settings button
        if (ui.showOpenSettingsButton && ui.settingsButtonText != null) {
            builder.addAction(
                0,
                ui.settingsButtonText,
                createSettingsPendingIntent(settingsReqCode)
            )
            builder.setContentIntent(createSettingsPendingIntent(settingsReqCode))
        }

        return builder
    }

    private fun createSettingsPendingIntent(requestId: Int): PendingIntent {
        val intent = Intent("android.settings.WIFI_ADB_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val finalIntent = if (context.packageManager.resolveActivity(intent, 0) != null) {
            intent
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = requestCodeBase + requestId
        return PendingIntent.getActivity(context, requestCode, finalIntent, flags)
    }
}

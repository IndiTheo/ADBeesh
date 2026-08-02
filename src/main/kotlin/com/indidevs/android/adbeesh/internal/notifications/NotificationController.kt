package com.indidevs.android.adbeesh.internal.notifications

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import com.indidevs.android.adbeesh.notifications.NotificationFlowConfig
import com.indidevs.android.adbeesh.pairing.AdbPairingService
import com.indidevs.android.adbeesh.pairing.PairingStage

/**
 * The bridge used to configure and control the notification-based pairing workflow.
 *
 * This object plugs the [NotificationDispatcher] into the [AdbPairingService] and
 * defines that the pairing process uses Android notifications.
 *
 * App components such as Providers and Callbacks use this interface to trigger
 * updates and manage the underlying service communication.
 */
internal object NotificationController {

    /** Updates the pairing session state and triggers a UI refresh. */
    fun update(
        context: Context,
        sessionId: String,
        config: NotificationFlowConfig,
        stage: PairingStage,
        port: Int? = null,
        receiver: Parcelable? = null,
        pairingCodeKey: String? = null,
        error: String? = null
    ) {
        val payload = Bundle().apply {
            putString(AdbPairingService.EXTRA_STAGE, stage.name)
            port?.let { putInt(AdbPairingService.EXTRA_PORT, it) }
            receiver?.let { putParcelable(AdbPairingService.EXTRA_RESULT_RECEIVER, it) }
            pairingCodeKey?.let { putString(AdbPairingService.EXTRA_PAIRING_CODE_KEY, it) }
            error?.let { putString(AdbPairingService.EXTRA_ERROR, it) }
        }
        send(context, sessionId, config, AdbPairingService.ACTION_UPDATE_SESSION, payload)
    }

    /** Terminates the pairing session and cleans up UI. */
    fun terminate(
        context: Context,
        sessionId: String,
        config: NotificationFlowConfig,
        stage: PairingStage? = null
    ) {
        val payload = stage?.let {
            Bundle().apply { putString(AdbPairingService.EXTRA_STAGE, it.name) }
        }
        send(context, sessionId, config, AdbPairingService.ACTION_TERMINATE_SESSION, payload)
    }

    private fun send(
        context: Context,
        sessionId: String,
        config: NotificationFlowConfig,
        action: String,
        payload: Parcelable?
    ) {
        AdbPairingService.dispatcher = NotificationDispatcher
        NotificationDispatcher.prepare(sessionId, config)

        val intent = Intent(context, AdbPairingService::class.java).apply {
            this.action = action
            putExtra(AdbPairingService.EXTRA_SESSION_ID, sessionId)
            putExtra(AdbPairingService.EXTRA_SESSION_DATA, payload)
        }

        if (action == AdbPairingService.ACTION_UPDATE_SESSION &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

package com.indidevs.android.adbeesh.notifications

import android.content.Context
import com.indidevs.android.adbeesh.internal.notifications.NotificationController
import com.indidevs.android.adbeesh.pairing.AdbPairingCallback
import com.indidevs.android.adbeesh.pairing.PairingStage

/**
 * Implementation of [AdbPairingCallback] that bridges core pairing events to the Android Notification system.
 *
 * This class automates the process of updating the pairing notification as the session progresses
 * through different [PairingStage]s. It uses the provided [NotificationFlowConfig] to determine the
 * visual configuration for each stage.
 *
 * @property context The Android [Context] used to interact with [NotificationController].
 * @property config The [NotificationFlowConfig] configuration that defines how notifications look and behave.
 */
class NotificationCallback(
    private val context: Context,
    private val config: NotificationFlowConfig = NotificationFlowConfig()
) : AdbPairingCallback {

    /**
     * Triggered when the pairing session begins.
     * Updates the notification to the [PairingStage.SEARCHING] stage.
     *
     * @param sessionId The unique ID of the pairing session.
     */
    override fun onPairingStarted(sessionId: String) {
        NotificationController.update(
            context,
            sessionId,
            config,
            PairingStage.SEARCHING
        )
    }

    /**
     * Triggered when the target device's pairing port is discovered.
     * Updates the notification to the [PairingStage.AWAITING_CODE] stage and displays the [port].
     *
     * @param sessionId The unique ID of the pairing session.
     * @param port The discovered ADB pairing port.
     */
    override fun onPortFound(sessionId: String, port: Int) {
        NotificationController.update(
            context,
            sessionId,
            config,
            PairingStage.AWAITING_CODE,
            port = port
        )
    }

    /**
     * Triggered when the cryptographic handshake with the device starts.
     * Updates the notification to the [PairingStage.PAIRING] stage.
     *
     * @param sessionId The unique ID of the pairing session.
     */
    override fun onPairingHandshakeStarted(sessionId: String) {
        NotificationController.update(
            context,
            sessionId,
            config,
            PairingStage.PAIRING
        )
    }

    /**
     * Triggered upon successful completion of the pairing process.
     * Terminates the active notification and optionally shows a [PairingStage.SUCCESS] stage.
     *
     * @param sessionId The unique ID of the pairing session.
     */
    override fun onPairingSuccess(sessionId: String) {
        NotificationController.terminate(context, sessionId, config, PairingStage.SUCCESS)
    }

    /**
     * Triggered when an error occurs during the pairing process.
     * Updates the notification to the [PairingStage.ERROR] stage and displays the [error] message.
     *
     * @param sessionId The unique ID of the pairing session.
     * @param error The error message describing the failure.
     */
    override fun onPairingError(sessionId: String, error: String) {
        NotificationController.update(
            context,
            sessionId,
            config,
            PairingStage.ERROR,
            error = error
        )
    }
}

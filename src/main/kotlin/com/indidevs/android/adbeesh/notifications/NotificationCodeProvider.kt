package com.indidevs.android.adbeesh.notifications

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.indidevs.android.adbeesh.internal.notifications.NotificationController
import com.indidevs.android.adbeesh.pairing.AdbPairingCodeProvider
import com.indidevs.android.adbeesh.pairing.AdbPairingService
import com.indidevs.android.adbeesh.pairing.PairingStage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Implementation of [AdbPairingCodeProvider] that collects the ADB pairing code via interactive Android notifications.
 *
 * This provider triggers an [AdbPairingService] update to show the [PairingStage.AWAITING_CODE] notification,
 * which includes a [androidx.core.app.RemoteInput] field for the user to enter the pairing code.
 *
 * @property context The Android [Context] used to interact with [NotificationController].
 * @property config The [NotificationFlowConfig] used to style the pairing notifications.
 */
class NotificationCodeProvider(
    private val context: Context,
    private val config: NotificationFlowConfig = NotificationFlowConfig()
) : AdbPairingCodeProvider {

    /**
     * Suspends the coroutine while waiting for the user to enter the pairing code in the notification.
     *
     * @param sessionId The unique ID of the pairing session.
     * @param port The discovered ADB pairing port.
     * @return The 6-digit pairing code entered by the user, or null if cancelled.
     */
    override suspend fun provideCode(sessionId: String, port: Int): String? =
        suspendCancellableCoroutine { continuation ->
            val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    val code = resultData?.getString(AdbPairingService.DEFAULT_PAIRING_CODE_KEY)
                    if (continuation.isActive) continuation.resume(code)
                }
            }

            NotificationController.update(
                context = context,
                sessionId = sessionId,
                config = config,
                stage = PairingStage.AWAITING_CODE,
                port = port,
                receiver = receiver,
                pairingCodeKey = AdbPairingService.DEFAULT_PAIRING_CODE_KEY
            )

            continuation.invokeOnCancellation {
                NotificationController.terminate(context, sessionId, config)
            }
        }
}

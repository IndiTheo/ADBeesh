package com.indidevs.android.adbeesh.internal.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.indidevs.android.adbeesh.internal.Adbeesh
import com.indidevs.android.adbeesh.notifications.NotificationFlowConfig
import com.indidevs.android.adbeesh.pairing.AdbPairingService
import com.indidevs.android.adbeesh.pairing.AdbPairingUpdate
import com.indidevs.android.adbeesh.pairing.PairingStage

internal class NotificationSession(sessionId: String) : AdbPairingService.PairingSession(
    sessionId
) {
    var config: NotificationFlowConfig = NotificationFlowConfig()

    val notificationId: Int
        get() = config.notificationId ?: (
            config.baseNotificationId + (sessionId.hashCode() and 0xFFFF)
            )

    /**
     * A stable, session-unique base for PendingIntent request codes.
     */
    val requestCodeBase: Int get() = config.baseRequestCode + (sessionId.hashCode() and 0xFFFF)
}

/**
 * Dispatcher for [AdbPairingService] that manages the Android notification UI.
 *
 * It transforms pairing events into user-visible notifications and handles foreground
 * service transitions. This dispatcher also processes direct user input from the
 * notification such as the pairing code.
 */
internal object NotificationDispatcher : AdbPairingService.PairingDispatcher {
    private const val TAG = "${Adbeesh.LOG_PREFIX}:NotificationDispatcher"

    internal const val INPUT_KEY = "input_pairing_code"
    internal const val EXTRA_IMMEDIATE_UI_FEEDBACK = "immediate_ui_feedback"
    internal const val ACTION_INPUT = "com.indidevs.android.adbeesh.pairing.action.PROCESS_INPUT"

    private const val REQ_SUBMIT_CODE = 100

    internal fun prepare(sessionId: String, config: NotificationFlowConfig) {
        val session = AdbPairingService.sessions[sessionId] as? NotificationSession
            ?: NotificationSession(sessionId).also { AdbPairingService.sessions[sessionId] = it }

        session.config = config
    }

    private var lastKeptNotificationId: Int? = null
    private var activeForegroundId: Int? = null

    override fun onCreateSession(sessionId: String): AdbPairingService.PairingSession =
        NotificationSession(sessionId)

    override fun onUpdateSession(
        service: Service,
        session: AdbPairingService.PairingSession,
        payload: Parcelable,
        isForeground: Boolean
    ) {
        val s = session as NotificationSession
        val ui = if (payload is Notification) payload else render(service, s).build()

        if (isForeground) {
            // Android allows only one Foreground Anchor per service.
            // Repeatedly calling startForeground() for the same ID triggers an OS anti-spam
            // measure that freezes notification updates. We track 'activeForegroundId'
            // to call startForeground() only once, then use notify() for fluid updates.
            if (activeForegroundId != s.notificationId) {
                service.promoteToForeground(s.notificationId, ui)
                activeForegroundId = s.notificationId
            } else {
                service.notifyNotification(s.notificationId, ui)
            }
        } else {
            service.notifyNotification(s.notificationId, ui)
        }
    }

    override fun onPromoteToForeground(
        service: Service,
        session: AdbPairingService.PairingSession
    ) {
        val s = session as NotificationSession
        val ui = render(service, s).build()
        service.promoteToForeground(s.notificationId, ui)
        activeForegroundId = s.notificationId
    }

    override fun onExitForeground(service: Service) {
        service.stopForegroundCompat(lastKeptNotificationId != null)
        lastKeptNotificationId = null
        activeForegroundId = null
    }

    override fun onStopSession(
        service: Service,
        session: AdbPairingService.PairingSession,
        finalPayload: Parcelable?
    ) {
        val s = session as NotificationSession

        val isSuccess = s.stage == PairingStage.SUCCESS

        if (isSuccess && finalPayload == null) {
            s.stage = PairingStage.SUCCESS
        }

        val ui = when {
            finalPayload is Notification -> finalPayload
            finalPayload is Bundle -> render(service, s).build()
            isSuccess -> render(service, s).build()
            else -> null
        }

        if (activeForegroundId == s.notificationId) {
            service.stopForegroundCompat(ui != null)
            activeForegroundId = null
        }

        if (ui != null) {
            service.notifyNotification(s.notificationId, ui)
            lastKeptNotificationId = s.notificationId
        } else {
            service.cancelNotification(s.notificationId)
            lastKeptNotificationId = null
        }
    }

    override fun onHandleInput(
        service: Service,
        intent: Intent,
        session: AdbPairingService.PairingSession
    ) {
        val s = session as NotificationSession
        val value = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(INPUT_KEY)?.toString()
        if (!value.isNullOrBlank()) {
            s.resultReceiver?.send(0, Bundle().apply { putString(s.pairingCodeKey, value) })

            val feedback = intent.getParcelableExtraCompat<Notification>(
                EXTRA_IMMEDIATE_UI_FEEDBACK
            )
            s.stage = PairingStage.CODE_SUBMITTED

            val isForeground = (service as? AdbPairingService)?.isSessionForeground(
                s.sessionId
            ) ?: false
            onUpdateSession(service, s, feedback ?: Bundle(), isForeground)
        }
    }

    override fun onServiceDestroyed() {
        activeForegroundId = null
        lastKeptNotificationId = null
    }

    private fun render(context: Context, session: NotificationSession): NotificationCompat.Builder {
        val view =
            NotificationView(
                context,
                session.config,
                session.sessionId,
                session.requestCodeBase
            )
        return when (session.stage) {
            PairingStage.SEARCHING -> view.createSearchingNotification()
            PairingStage.AWAITING_CODE -> {
                val action = buildPairAction(context, session, view)
                view.createAwaitingCodeNotification(session.port ?: 0, action)
            }
            PairingStage.CODE_SUBMITTED -> view.createCodeSubmittedNotification()
            PairingStage.PAIRING -> view.createPairingNotification()
            PairingStage.SUCCESS -> view.createSuccessNotification()
            PairingStage.ERROR -> view.createErrorNotification(
                session.error ?: "Unknown error"
            )
        }
    }

    private fun buildPairAction(
        context: Context,
        session: NotificationSession,
        view: NotificationView
    ): NotificationCompat.Action? {
        val ui = session.config.awaitingCode(AdbPairingUpdate(session.sessionId, session.port))
        val receiver = session.resultReceiver

        if (receiver == null || ui.actionButtonText == null) return null

        val remoteInput = RemoteInput.Builder(INPUT_KEY)
            .setLabel(ui.inputLabel ?: "Code")
            .build()

        val intent = Intent(context, AdbPairingService::class.java).apply {
            action = ACTION_INPUT
            putExtra(AdbPairingService.EXTRA_SESSION_ID, session.sessionId)
            putExtra(AdbPairingService.EXTRA_RESULT_RECEIVER, receiver)
            putExtra(EXTRA_IMMEDIATE_UI_FEEDBACK, view.createCodeSubmittedNotification().build())
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val requestCode = session.requestCodeBase + REQ_SUBMIT_CODE
        val pendingIntent = PendingIntent.getService(context, requestCode, intent, flags)

        return NotificationCompat.Action.Builder(0, ui.actionButtonText, pendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun Service.promoteToForeground(id: Int, ui: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            if (type != 0) startForeground(id, ui, type) else startForeground(id, ui)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun Service.notifyNotification(id: Int, ui: Notification) =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, ui)

    private fun Service.cancelNotification(id: Int) =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)

    private fun Service.stopForegroundCompat(keepNotification: Boolean) {
        val flag = if (keepNotification) {
            Service.STOP_FOREGROUND_DETACH
        } else {
            Service.STOP_FOREGROUND_REMOVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(flag)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(!keepNotification)
        }
    }

    private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
        }
}

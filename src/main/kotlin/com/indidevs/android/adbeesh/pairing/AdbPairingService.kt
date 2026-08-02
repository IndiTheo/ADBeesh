package com.indidevs.android.adbeesh.pairing

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.os.ResultReceiver
import android.util.Log
import com.indidevs.android.adbeesh.internal.Adbeesh
import java.util.concurrent.ConcurrentHashMap

/**
 * A headless background orchestrator for ADB pairing.
 *
 * This service acts as a lifecycle anchor. It doesn't manage UI itself,
 * but provides the necessary [Service] context that Android requires to
 * run in the foreground.
 *
 * It acts as a router, receiving Intents and forwarding them to a
 * static [PairingDispatcher].
 */
class AdbPairingService : Service() {
    companion object {
        private const val TAG = "${Adbeesh.LOG_PREFIX}:Service"

        /** The active dispatcher for pairing events. Set this before starting the service. */
        @JvmStatic
        var dispatcher: PairingDispatcher? = null

        private val BASE_ACTION_NAME = AdbPairingService::class.java.name

        val ACTION_UPDATE_SESSION = "$BASE_ACTION_NAME.action.UPDATE_SESSION"
        val ACTION_TERMINATE_SESSION = "$BASE_ACTION_NAME.action.TERMINATE_SESSION"

        const val EXTRA_SESSION_DATA = "extra_session_data"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_RESULT_RECEIVER = "extra_result_receiver"
        const val EXTRA_PAIRING_CODE_KEY = "extra_pairing_code_key"

        const val EXTRA_STAGE = "session_stage"
        const val EXTRA_PORT = "adb_port"
        const val EXTRA_ERROR = "error_message"

        /** The default key used in the result bundle for the pairing code. */
        const val DEFAULT_PAIRING_CODE_KEY = "pairing_code"

        /** Active pairing sessions. */
        @JvmStatic
        val sessions = ConcurrentHashMap<String, PairingSession>()
    }

    /** Generic business state for an ADB pairing session. */
    open class PairingSession(
        /** The unique identifier for this session. */
        val sessionId: String,

        /** The current [PairingStage] of the process. */
        var stage: PairingStage = PairingStage.SEARCHING,

        /** The network port discovered for pairing, if any. */
        var port: Int? = null,

        /** Error description if the session reached an [PairingStage.ERROR] state. */
        var error: String? = null,

        /** Callback mechanism for providing interactive feedback (e.g., the pairing code). */
        var resultReceiver: ResultReceiver? = null,

        /** The key used to identify the pairing code in the [resultReceiver]'s result bundle. */
        var pairingCodeKey: String = "pairing_code"
    )

    /**
     * Delegates UI and interactive state management.
     */
    interface PairingDispatcher {
        /** Called when a new session object needs to be created. */
        fun onCreateSession(sessionId: String): PairingSession

        /** Called when a session is updated. */
        fun onUpdateSession(
            service: Service,
            session: PairingSession,
            payload: Parcelable,
            isForeground: Boolean
        )

        /** Called when a session should be promoted to the foreground anchor. */
        fun onPromoteToForeground(service: Service, session: PairingSession)

        /** Called when the service should exit the foreground state. */
        fun onExitForeground(service: Service)

        /** Called when a session is stopped. */
        fun onStopSession(service: Service, session: PairingSession, finalPayload: Parcelable?)

        /** Called when remote input needs handling. */
        fun onHandleInput(service: Service, intent: Intent, session: PairingSession)

        /** Called when the service is destroyed. */
        fun onServiceDestroyed()
    }

    private var foregroundSessionId: String? = null

    internal fun isSessionForeground(sessionId: String): Boolean = foregroundSessionId == sessionId

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""

        val dispatcher = dispatcher
        if (dispatcher == null) {
            Log.e(TAG, "[$sessionId] No dispatcher registered for $action")
            return START_NOT_STICKY
        }

        // Only allow new session creation via the factory on UPDATE actions.
        // This stops "zombie" sessions from re-animating
        val session = if (action == ACTION_UPDATE_SESSION) {
            sessions.getOrPut(sessionId) { dispatcher.onCreateSession(sessionId) }
        } else {
            sessions[sessionId] ?: return START_NOT_STICKY
        }

        // Sync session with Intent-level protocol extras
        intent.getParcelableExtraCompat<ResultReceiver>(EXTRA_RESULT_RECEIVER)?.let {
            session.resultReceiver = it
        }
        intent.getStringExtra(EXTRA_PAIRING_CODE_KEY)?.let {
            session.pairingCodeKey = it
        }

        val payload = intent.getParcelableExtraCompat<Parcelable>(EXTRA_SESSION_DATA)

        // Merge payload into session state
        if (payload is Bundle) {
            payload.getString(EXTRA_STAGE)?.let {
                try {
                    session.stage = PairingStage.valueOf(it)
                } catch (_: Exception) {}
            }
            if (payload.containsKey(EXTRA_PORT)) {
                session.port = payload.getInt(EXTRA_PORT)
            }
            payload.getString(EXTRA_ERROR)?.let { session.error = it }
            payload.getParcelableCompat<ResultReceiver>(EXTRA_RESULT_RECEIVER)?.let {
                session.resultReceiver = it
            }
            payload.getString(EXTRA_PAIRING_CODE_KEY)?.let {
                session.pairingCodeKey = it
            }
        }

        return processAction(dispatcher, action, session, payload, intent)
    }

    private fun processAction(
        dispatcher: PairingDispatcher,
        action: String,
        session: PairingSession,
        payload: Parcelable?,
        intent: Intent
    ): Int {
        Log.d(
            TAG,
            "processAction: action=$action session=${session.sessionId} stage=${session.stage}"
        )
        when (action) {
            ACTION_UPDATE_SESSION -> {
                if (foregroundSessionId == null) {
                    foregroundSessionId = session.sessionId
                }
                if (payload != null) {
                    // designating 'isForeground = true' tells the dispatcher that this session
                    // is the primary "anchor" responsible for keeping the service alive in the foreground.
                    dispatcher.onUpdateSession(
                        this,
                        session,
                        payload,
                        foregroundSessionId == session.sessionId
                    )
                }
            }
            ACTION_TERMINATE_SESSION -> {
                val wasForeground = foregroundSessionId == session.sessionId
                dispatcher.onStopSession(this, session, payload)
                sessions.remove(session.sessionId)

                if (wasForeground) {
                    foregroundSessionId = sessions.keys().asSequence().firstOrNull()
                    foregroundSessionId?.let { nextId ->
                        sessions[nextId]?.let { nextSession ->
                            dispatcher.onPromoteToForeground(this, nextSession)
                        }
                    } ?: dispatcher.onExitForeground(this)
                }
            }
            else -> dispatcher.onHandleInput(this, intent, session)
        }

        if (sessions.isEmpty()) {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        dispatcher?.onServiceDestroyed()
        sessions.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
                as? T
        }

    private inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelable(key)
                as? T
        }
}

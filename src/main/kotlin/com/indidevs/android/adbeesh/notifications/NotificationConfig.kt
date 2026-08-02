package com.indidevs.android.adbeesh.notifications

import androidx.annotation.DrawableRes
import com.indidevs.android.adbeesh.pairing.AdbPairingUpdate
import com.indidevs.android.adbeesh.pairing.PairingStage

/**
 * DSL Marker to prevent scope leaking in the notification configuration.
 */
@DslMarker
annotation class AdbeeshNotificationDsl

/**
 * Interface defining all configurable UI properties for a notification.
 *
 * All properties support both direct assignment (Kotlin properties) and
 * fluent chaining (methods) for compatibility with Java and Builder patterns.
 */
@AdbeeshNotificationDsl
interface NotificationUiBuilder {
    /** The main title of the notification. */
    var title: String?

    /** Sets the title and returns the builder for chaining. */
    fun title(value: String?): NotificationUiBuilder = apply { title = value }

    /** The primary content text. */
    var text: String?

    /** Sets the text and returns the builder for chaining. */
    fun text(value: String?): NotificationUiBuilder = apply { text = value }

    /** Optional expanded text for [androidx.core.app.NotificationCompat.BigTextStyle]. */
    var bigText: String?

    /** Sets the expanded text and returns the builder for chaining. */
    fun bigText(value: String?): NotificationUiBuilder = apply { bigText = value }

    /** Optional text for the primary action button. */
    var actionButtonText: String?

    /** Sets the action button text and returns the builder for chaining. */
    fun actionButtonText(value: String?): NotificationUiBuilder = apply { actionButtonText = value }

    /** Optional text for the deep-link settings button. */
    var settingsButtonText: String?

    /** Sets the settings button text and returns the builder for chaining. */
    fun settingsButtonText(value: String?): NotificationUiBuilder =
        apply { settingsButtonText = value }

    /** Optional hint for the [androidx.core.app.RemoteInput] text field. */
    var inputLabel: String?

    /** Sets the input label and returns the builder for chaining. */
    fun inputLabel(value: String?): NotificationUiBuilder = apply { inputLabel = value }

    /** The drawable resource ID for the notification's small icon. */
    @get:DrawableRes
    @set:DrawableRes
    var smallIconRes: Int?

    /** Sets the small icon resource and returns the builder for chaining. */
    fun smallIconRes(@DrawableRes value: Int?): NotificationUiBuilder =
        apply { smallIconRes = value }

    /** Whether to show a shortcut button to the Wireless Debugging settings. */
    var showOpenSettingsButton: Boolean?

    /** Sets whether to show the settings button and returns the builder for chaining. */
    fun showOpenSettingsButton(value: Boolean?): NotificationUiBuilder =
        apply { showOpenSettingsButton = value }
}

/**
 * Functional interface for configuring notification UI.
 *
 * In Kotlin, this supports a DSL-like syntax with an implicit receiver.
 * In Java, this behaves as a standard functional interface (SAM).
 */
fun interface AdbeeshNotificationAction {
    /**
     * Applies configuration to the provided [NotificationUiBuilder].
     *
     * @param update The current pairing state update.
     */
    fun NotificationUiBuilder.configure(update: AdbPairingUpdate)
}

/**
 * Unified UI configuration for a single notification state.
 *
 * This class describes how a single notification should look at any given moment,
 * regardless of the [PairingStage]. It is used as a building block for [NotificationFlowConfig].
 */
data class NotificationConfig(
    /** The main title of the notification. */
    val title: String,
    /** The primary content text. */
    val text: String,
    /** Optional expanded text for [androidx.core.app.NotificationCompat.BigTextStyle]. */
    val bigText: String? = null,
    /** Optional text for the primary action button. */
    val actionButtonText: String? = null,
    /** Optional text for the deep-link settings button. */
    val settingsButtonText: String? = null,
    /** Optional hint for the [androidx.core.app.RemoteInput] text field. */
    val inputLabel: String? = null,
    /** The drawable resource ID for the notification's small icon. */
    @DrawableRes val smallIconRes: Int = NotificationFlowConfig.DEFAULT_SMALL_ICON,
    /** Whether to show a shortcut button to the Wireless Debugging settings. */
    val showOpenSettingsButton: Boolean = NotificationFlowConfig.DEFAULT_SHOW_SETTINGS
) {
    /**
     * Internal builder used to implement the [NotificationFlowConfig] DSL.
     *
     * @property base The base configuration to start from.
     */
    internal class Builder(private val base: NotificationConfig) : NotificationUiBuilder {
        override var title: String? = base.title
        override var text: String? = base.text
        override var bigText: String? = base.bigText
        override var actionButtonText: String? = base.actionButtonText
        override var settingsButtonText: String? = base.settingsButtonText
        override var inputLabel: String? = base.inputLabel
        override var smallIconRes: Int? = base.smallIconRes
        override var showOpenSettingsButton: Boolean? = base.showOpenSettingsButton

        /**
         * Builds the modified [NotificationConfig] instance.
         *
         * @return The new [NotificationConfig] instance.
         */
        fun build() = NotificationConfig(
            title ?: base.title,
            text ?: base.text,
            bigText ?: base.bigText,
            actionButtonText ?: base.actionButtonText,
            settingsButtonText ?: base.settingsButtonText,
            inputLabel ?: base.inputLabel,
            smallIconRes ?: base.smallIconRes,
            showOpenSettingsButton ?: base.showOpenSettingsButton
        )
    }
}

/**
 * The master configuration container for the ADB pairing notification flow.
 *
 * This class manages the global branding and holds separate [NotificationConfig]
 * instances for every [PairingStage] of the pairing process.
 */
class NotificationFlowConfig(
    /** The ID of the Android notification channel. */
    val notificationChannelId: String = DEFAULT_CHANNEL_ID,
    /** The user-visible name of the notification channel. */
    val notificationChannelName: String = DEFAULT_CHANNEL_NAME,
    /** An optional fixed notification ID. */
    val notificationId: Int? = DEFAULT_NOTIFICATION_ID,
    /** The base value used for generating dynamic notification IDs. */
    val baseNotificationId: Int = DEFAULT_BASE_NOTIFICATION_ID,
    /** The base value used for generating [android.app.PendingIntent] request codes. */
    val baseRequestCode: Int = DEFAULT_BASE_REQUEST_CODE,

    /** [NotificationConfig] factory used during the [PairingStage.SEARCHING] stage. */
    val searching: (AdbPairingUpdate) -> NotificationConfig = DEFAULT_SEARCHING,
    /** [NotificationConfig] factory used during the [PairingStage.AWAITING_CODE] stage. */
    val awaitingCode: (AdbPairingUpdate) -> NotificationConfig = DEFAULT_AWAITING_CODE,
    /** [NotificationConfig] factory used during the [PairingStage.CODE_SUBMITTED] stage. */
    val codeSubmitted: (AdbPairingUpdate) -> NotificationConfig = DEFAULT_CODE_SUBMITTED,
    /** [NotificationConfig] factory used during the [PairingStage.PAIRING] stage. */
    val pairing: (AdbPairingUpdate) -> NotificationConfig = DEFAULT_PAIRING,
    /** [NotificationConfig] factory used during the [PairingStage.SUCCESS] stage. */
    val success: (AdbPairingUpdate) -> NotificationConfig = DEFAULT_SUCCESS,
    /** [NotificationConfig] factory used during the [PairingStage.ERROR] stage. */
    val error: (AdbPairingUpdate) -> NotificationConfig = DEFAULT_ERROR
) {
    /**
     * Builder for fluent configuration of the entire [NotificationFlowConfig].
     *
     * Functions called directly on the builder act as "common" overrides for all [PairingStage]s.
     */
    @AdbeeshNotificationDsl
    class Builder {
        /** The ID of the Android notification channel. */
        var channelId: String = DEFAULT_CHANNEL_ID

        /** The user-visible name of the notification channel. */
        var channelName: String = DEFAULT_CHANNEL_NAME

        /** An optional fixed notification ID. */
        var notificationId: Int? = DEFAULT_NOTIFICATION_ID

        /** The base value used for generating dynamic notification IDs. */
        var baseNotificationId: Int = DEFAULT_BASE_NOTIFICATION_ID

        /** The base value used for generating [android.app.PendingIntent] request codes. */
        var baseRequestCode: Int = DEFAULT_BASE_REQUEST_CODE

        private var searchingModifier: AdbeeshNotificationAction? = null
        private var awaitingCodeModifier: AdbeeshNotificationAction? = null
        private var codeSubmittedModifier: AdbeeshNotificationAction? = null
        private var pairingModifier: AdbeeshNotificationAction? = null
        private var successModifier: AdbeeshNotificationAction? = null
        private var errorModifier: AdbeeshNotificationAction? = null
        private var commonModifier: AdbeeshNotificationAction? = null

        /**
         * Applies common branding to all [PairingStage]s.
         *
         * @param action A configuration action that takes the current [AdbPairingUpdate].
         * @return The builder instance.
         */
        fun common(action: AdbeeshNotificationAction) = apply {
            val old = commonModifier
            commonModifier = AdbeeshNotificationAction { u ->
                old?.run { configure(u) }
                action.run { configure(u) }
            }
        }

        /**
         * Configures the Android [android.app.NotificationChannel].
         *
         * @param id The unique ID of the channel.
         * @param name The user-visible name of the channel.
         * @return The builder instance.
         */
        fun channel(id: String, name: String) = apply {
            channelId = id
            channelName = name
        }

        /**
         * Sets a fixed notification ID for the entire session.
         *
         * @param id The notification ID.
         * @return The builder instance.
         */
        fun notificationId(id: Int?) = apply { notificationId = id }

        /**
         * Sets the base value used for generating dynamic notification IDs.
         *
         * @param id The base notification ID.
         * @return The builder instance.
         */
        fun baseNotificationId(id: Int) = apply { baseNotificationId = id }

        /**
         * Sets the base value used for generating PendingIntent request codes.
         *
         * @param code The base request code.
         * @return The builder instance.
         */
        fun baseRequestCode(code: Int) = apply { baseRequestCode = code }

        private fun NotificationConfig.edit(block: NotificationUiBuilder.() -> Unit) =
            NotificationConfig.Builder(this).apply(block).build()

        /**
         * DSL block for configuring the [PairingStage.SEARCHING] stage.
         *
         * @param action A configuration action that takes the current [AdbPairingUpdate].
         * @return The builder instance.
         */
        fun searching(action: AdbeeshNotificationAction) = apply {
            val old = searchingModifier
            searchingModifier = AdbeeshNotificationAction { u ->
                old?.run { configure(u) }
                action.run { configure(u) }
            }
        }

        /**
         * DSL block for configuring the [PairingStage.AWAITING_CODE] stage.
         *
         * @param action A configuration action that takes the current [AdbPairingUpdate].
         * @return The builder instance.
         */
        fun awaitingCode(action: AdbeeshNotificationAction) = apply {
            val old = awaitingCodeModifier
            awaitingCodeModifier = AdbeeshNotificationAction { u ->
                old?.run { configure(u) }
                action.run { configure(u) }
            }
        }

        /**
         * DSL block for configuring the [PairingStage.CODE_SUBMITTED] stage.
         *
         * @param action A configuration action that takes the current [AdbPairingUpdate].
         * @return The builder instance.
         */
        fun codeSubmitted(action: AdbeeshNotificationAction) = apply {
            val old = codeSubmittedModifier
            codeSubmittedModifier = AdbeeshNotificationAction { u ->
                old?.run { configure(u) }
                action.run { configure(u) }
            }
        }

        /**
         * DSL block for configuring the [PairingStage.PAIRING] stage.
         *
         * @param action A configuration action that takes the current [AdbPairingUpdate].
         * @return The builder instance.
         */
        fun pairing(action: AdbeeshNotificationAction) = apply {
            val old = pairingModifier
            pairingModifier = AdbeeshNotificationAction { u ->
                old?.run { configure(u) }
                action.run { configure(u) }
            }
        }

        /**
         * DSL block for configuring the [PairingStage.SUCCESS] stage.
         *
         * @param action A configuration action that takes the current [AdbPairingUpdate].
         * @return The builder instance.
         */
        fun success(action: AdbeeshNotificationAction) = apply {
            val old = successModifier
            successModifier = AdbeeshNotificationAction { u ->
                old?.run { configure(u) }
                action.run { configure(u) }
            }
        }

        /**
         * DSL block for configuring the [PairingStage.ERROR] stage.
         *
         * @param action A configuration action that takes the current [AdbPairingUpdate].
         * @return The builder instance.
         */
        fun error(action: AdbeeshNotificationAction) = apply {
            val old = errorModifier
            errorModifier = AdbeeshNotificationAction { u ->
                old?.run { configure(u) }
                action.run { configure(u) }
            }
        }

        /**
         * Builds the final [NotificationFlowConfig] instance.
         *
         * @return The new [NotificationFlowConfig] instance.
         */
        fun build(): NotificationFlowConfig {
            fun wrap(
                factory: (AdbPairingUpdate) -> NotificationConfig,
                stageModifier: AdbeeshNotificationAction?
            ): (AdbPairingUpdate) -> NotificationConfig = { update ->
                factory(update).edit {
                    this@Builder.commonModifier?.run { configure(update) }
                    stageModifier?.run { configure(update) }
                }
            }

            return NotificationFlowConfig(
                notificationChannelId = channelId,
                notificationChannelName = channelName,
                notificationId = notificationId,
                baseNotificationId = baseNotificationId,
                baseRequestCode = baseRequestCode,
                searching = wrap(DEFAULT_SEARCHING, searchingModifier),
                awaitingCode = wrap(DEFAULT_AWAITING_CODE, awaitingCodeModifier),
                codeSubmitted = wrap(DEFAULT_CODE_SUBMITTED, codeSubmittedModifier),
                pairing = wrap(DEFAULT_PAIRING, pairingModifier),
                success = wrap(DEFAULT_SUCCESS, successModifier),
                error = wrap(DEFAULT_ERROR, errorModifier)
            )
        }
    }

    companion object {
        /** The default ID for the Android [android.app.NotificationChannel]. */
        const val DEFAULT_CHANNEL_ID = "adbeesh_pairing_channel_v0"

        /** The default user-visible name for the [android.app.NotificationChannel]. */
        const val DEFAULT_CHANNEL_NAME = "ADB Pairing Discovery"

        /** The default base value used for generating dynamic notification IDs. */
        const val DEFAULT_BASE_NOTIFICATION_ID = 10000

        /** The default base value used for generating [android.app.PendingIntent] request codes. */
        const val DEFAULT_BASE_REQUEST_CODE = 1000

        /** The default setting for whether to show the settings button. */
        const val DEFAULT_SHOW_SETTINGS = true

        /** The default drawable resource ID for the notification's small icon. */
        @DrawableRes val DEFAULT_SMALL_ICON = android.R.drawable.ic_dialog_info

        /** The default fixed notification ID. `null` indicates dynamic ID generation. */
        val DEFAULT_NOTIFICATION_ID: Int? = null

        /** Default [NotificationConfig] factory used during the [PairingStage.SEARCHING] stage. */
        private val DEFAULT_SEARCHING: (AdbPairingUpdate) -> NotificationConfig = {
            NotificationConfig(
                title = "Wireless Debugging Required",
                text = "Tap here and go to \"Wireless Debugging\"",
                bigText = "Go to \"Wireless Debugging\" in Developer Options.\n" +
                    "Tap \"Pair device with pairing code\".",
                settingsButtonText = "OPEN SETTINGS"
            )
        }

        /** Default [NotificationConfig] factory used during the [PairingStage.AWAITING_CODE] stage. */
        private val DEFAULT_AWAITING_CODE: (AdbPairingUpdate) -> NotificationConfig = { update ->
            val port = update.port ?: 0
            NotificationConfig(
                title = "Enter Pairing Code",
                text = "Port $port found. Enter Wi-Fi pairing code:",
                actionButtonText = "ENTER CODE",
                settingsButtonText = "OPEN SETTINGS",
                inputLabel = "Wi-Fi pairing code"
            )
        }

        /** Default [NotificationConfig] factory used during the [PairingStage.CODE_SUBMITTED] stage. */
        private val DEFAULT_CODE_SUBMITTED: (AdbPairingUpdate) -> NotificationConfig = {
            NotificationConfig(
                title = "Code Submitted",
                text = "Handshaking with device...",
                showOpenSettingsButton = false
            )
        }

        /** Default [NotificationConfig] factory used during the [PairingStage.PAIRING] stage. */
        private val DEFAULT_PAIRING: (AdbPairingUpdate) -> NotificationConfig = {
            NotificationConfig(
                title = "Connecting...",
                text = "Pairing ADB via Wireless Debugging.",
                showOpenSettingsButton = false
            )
        }

        /** Default [NotificationConfig] factory used during the [PairingStage.SUCCESS] stage. */
        private val DEFAULT_SUCCESS: (AdbPairingUpdate) -> NotificationConfig = {
            NotificationConfig(
                title = "Connected!",
                text = "ADB pairing successful.",
                showOpenSettingsButton = false
            )
        }

        /** Default [NotificationConfig] factory during the [PairingStage.ERROR] stage. */
        private val DEFAULT_ERROR: (AdbPairingUpdate) -> NotificationConfig = { update ->
            val msg = update.error ?: "Unknown error"
            NotificationConfig(
                title = "Pairing Failed",
                text = "Error: $msg"
            )
        }
    }
}

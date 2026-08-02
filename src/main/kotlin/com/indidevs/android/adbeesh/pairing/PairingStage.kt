package com.indidevs.android.adbeesh.pairing

/** The different phases of the ADB pairing process. */
enum class PairingStage {
    SEARCHING,
    AWAITING_CODE,
    CODE_SUBMITTED,
    PAIRING,
    SUCCESS,
    ERROR
}

package com.indidevs.android.adbeesh.runtime

import com.flyfishxu.kadb.Kadb
import java.io.Closeable

/**
 * A live connection to an ADB device.
 */
interface AdbSession : Closeable {
    /**
     * Executes a shell command on the device.
     * @param command The command to execute.
     * @return The result of the command execution.
     */
    suspend fun shell(command: String): AdbShellResult

    /**
     * The underlying [com.flyfishxu.kadb.Kadb] instance for advanced operations.
     */
    val kadb: Kadb

    /**
     * Checks if the connection is still active.
     */
    suspend fun isAlive(): Boolean

    /**
     * Closes the connection.
     */
    override fun close()
}

/**
 * Result of a shell command execution.
 */
data class AdbShellResult(
    val exitCode: Int,
    val output: String,
    val error: String? = null
) {
    val isSuccess: Boolean get() = exitCode == 0
}

package com.indidevs.android.adbeesh.internal.connection

import com.flyfishxu.kadb.Kadb
import com.indidevs.android.adbeesh.runtime.AdbSession
import com.indidevs.android.adbeesh.runtime.AdbShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [AdbSession] wrapping a [com.flyfishxu.kadb.Kadb] instance.
 */
internal class AdbSessionImpl(override val kadb: Kadb) : AdbSession {
    override suspend fun shell(command: String): AdbShellResult = withContext(Dispatchers.IO) {
        val response = kadb.shell(command)
        AdbShellResult(
            exitCode = response.exitCode,
            output = response.output,
            error = response.errorOutput.takeIf { it.isNotEmpty() }
        )
    }

    override suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        kadb.connectionCheck()
    }

    override fun close() {
        try {
            kadb.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}

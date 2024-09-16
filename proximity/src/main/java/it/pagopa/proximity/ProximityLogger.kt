package it.pagopa.proximity

import android.util.Log

object ProximityLogger {
    var enabled = false
    fun v(tag: String, message: String) {
        if (enabled)
            Log.v(tag, message)
    }

    fun e(tag: String, message: String) {
        if (enabled)
            Log.e(tag, message)
    }

    fun d(tag: String, message: String) {
        if (enabled)
            Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (enabled)
            Log.i(tag, message)
    }
}
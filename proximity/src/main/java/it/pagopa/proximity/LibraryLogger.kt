package it.pagopa.proximity

import android.util.Log

object LibraryLogger {
    var enabled = false
    fun v(tag: String, message: String) {
        if (enabled)
            message.sendLog(tag, KindOfLog.Verbose)
    }

    fun e(tag: String, message: String) {
        if (enabled)
            message.sendLog(tag, KindOfLog.Error)
    }

    fun d(tag: String, message: String) {
        if (enabled)
            message.sendLog(tag, KindOfLog.Debug)
    }

    fun i(tag: String, message: String) {
        if (enabled)
            message.sendLog(tag, KindOfLog.Info)
    }

    private fun String.sendLog(tag: String, kindOfLog: KindOfLog) {
        when (kindOfLog) {
            KindOfLog.Debug -> Log.d(tag, this)
            KindOfLog.Error -> Log.e(tag, this)
            KindOfLog.Verbose -> Log.v(tag, this)
            KindOfLog.Info -> Log.i(tag, this)
        }
    }
}

enum class KindOfLog {
    Info,
    Verbose,
    Error,
    Debug
}
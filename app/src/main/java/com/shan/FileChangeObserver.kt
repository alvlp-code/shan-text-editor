package com.shan

import android.os.FileObserver
import android.util.Log
import java.io.File

class FileChangeObserver(
    private val path: String,
    private val onEvent: (event: Int, path: String?) -> Unit
) : FileObserver(path, FileObserver.ALL_EVENTS) {

    override fun onEvent(event: Int, path: String?) {
        // Filter relevant events - using correct constants
        val relevantEvents = event and (
                FileObserver.CREATE or
                        FileObserver.DELETE or
                        FileObserver.MOVED_FROM or
                        FileObserver.MOVED_TO or
                        FileObserver.MODIFY or
                        FileObserver.DELETE_SELF
                )

        if (relevantEvents != 0) {
            Log.d("FileChangeObserver", "Event: $event, Path: $path")
            onEvent(event, path)
        }
    }
}
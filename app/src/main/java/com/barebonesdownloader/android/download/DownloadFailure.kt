package com.barebonesdownloader.android.download

data class DownloadFailure(val code: String, val message: String, val diagnostic: String)
fun describeDownloadFailure(error: Exception): DownloadFailure {
    val detail = sanitizeDiagnostic(error.message ?: "Download failed.")
    val text = detail.lowercase()
    val (code, message) = when {
        listOf("unsupported url", "requested format is not available", "no suitable extractor", "no video formats", "no video could be found", "collections are rejected", "live streams are not supported", "drm-protected").any(text::contains) ->
            "UNSUPPORTED" to "This link does not expose a supported public single-media item. Playlists, live streams and DRM-protected media are unsupported."
        listOf("native runtime is missing", "missing ffmpeg encoder", "no such file or directory").any(text::contains) -> "TOOL_MISSING" to "A required media tool is missing or unsupported on this device. Reinstall a compatible APK."
        error is com.barebonesdownloader.android.media.SizeLimitImpossible -> "SIZE_IMPOSSIBLE" to detail
        listOf("sign in", "sign-in", "not a bot", "authentication", "login required", "use --cookies").any(text::contains) ->
            "AUTH_REQUIRED" to "This source requires sign-in or verification. Authenticated downloads are not supported; no file was saved."
        listOf("no space left", "disk full", "enospc").any(text::contains) ->
            "NO_SPACE" to "There is not enough storage for this download. Free space and retry."
        listOf("timed out", "time limit", "timeout").any(text::contains) ->
            "TIMEOUT" to "The source or media tool timed out. Retry later."
        listOf("video is unavailable", "video unavailable", "has been removed", "private video").any(text::contains) ->
            "UNAVAILABLE" to "This video is unavailable or private. Try an accessible single-video link."
        else -> "DOWNLOAD" to detail
    }
    return DownloadFailure(code, message, detail)
}

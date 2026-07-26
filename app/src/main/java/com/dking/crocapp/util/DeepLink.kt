package com.dking.crocapp.util

import android.net.Uri

/**
 * A parsed receive target: the transfer code plus any connection settings the
 * sender embedded in the link (applied to that receive only). `local` mirrors
 * croc's --local; `relay` is tolerated for forward-compatibility.
 */
data class ReceiveTarget(
    val code: String,
    val local: Boolean = false,
    val relay: String? = null,
    /** The croc version the SENDER transfers with (`&v=`), e.g. "10.6.0". */
    val crocVersion: String? = null
)

/**
 * Parse a scanned QR / pasted string into a [ReceiveTarget]. Accepts a bare code,
 * or a deep link — croc://receive?code=…&local=1, croc://<code>, or
 * https://…/croc/receive?code=… — reading the code and any embedded settings.
 */
fun parseReceiveTarget(raw: String): ReceiveTarget {
    val t = raw.trim()
    val uri = runCatching { Uri.parse(t) }.getOrNull()
    if (uri != null && (uri.scheme == "croc" || uri.scheme == "http" || uri.scheme == "https")) {
        val code = uri.getQueryParameter("code")?.trim()?.takeIf { it.isNotEmpty() }
            ?: (uri.host ?: uri.lastPathSegment)?.trim()?.takeIf { it.isNotEmpty() && it != "receive" }
        if (code != null) {
            val local = uri.getQueryParameter("local")?.lowercase() in listOf("1", "true", "yes")
            val relay = uri.getQueryParameter("relay")?.trim()?.takeIf { it.isNotEmpty() }
            val crocVersion = uri.getQueryParameter("v")?.trim()?.takeIf { it.isNotEmpty() }
            return ReceiveTarget(code, local, relay, crocVersion)
        }
    }
    return ReceiveTarget(t)
}

/**
 * Turn a scanned QR value (or a pasted string) into just a croc transfer code.
 * Accepts a bare code or any of the deep-link forms. (Thin wrapper over
 * [parseReceiveTarget] for callers that only need the code.)
 */
fun extractCrocCode(raw: String): String = parseReceiveTarget(raw).code

/** The shareable https link that opens the app into receiving this code
 *  (verified App Link; falls back to the web page + install). Used by "Copy link". */
fun receiveLink(code: String, local: Boolean = false): String {
    val base = "https://carlos-err406.github.io/croc/receive?code=" + Uri.encode(code.trim())
    return (if (local) "$base&local=1" else base) + crocVersionParam()
}

/** The croc:// deep link for a code. The QR encodes THIS (not the bare code or the
 *  https link): scanning it with a phone camera / QR app opens the app directly via
 *  the custom scheme, whereas a scanned https App Link doesn't reliably hand off and
 *  a bare code isn't a link at all. Every in-app scanner still parses the code out. */
fun receiveDeepLink(code: String, local: Boolean = false): String {
    val base = "croc://receive?code=" + Uri.encode(code.trim())
    return (if (local) "$base&local=1" else base) + crocVersionParam()
}

/** `&v=<croc version>` — the croc version this app transfers with, so the receiver can
 *  compare major.minor against its own and warn on a mismatch (croc doesn't
 *  interoperate across minor lines). Readers that don't know `v` ignore it. */
private fun crocVersionParam(): String = "&v=" + Uri.encode(CROC_VERSION)

/** The croc version this app bundles. Mirrors CrocBinaryManager.BINARY_VERSION. */
const val CROC_VERSION: String = "10.6.0"

/** major.minor of a croc version string ("10.6.0" / "v10.6.0" -> "10.6"), or null. */
fun crocMinor(v: String?): String? =
    Regex("""^(\d+)\.(\d+)""").find((v ?: "").trim().removePrefix("v").removePrefix("V"))
        ?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }

/** A sender/our croc version mismatch worth warning about, or null when either side is
 *  unknown or they share a minor line (never cry wolf on a typed code). */
data class VersionMismatch(val sender: String, val ours: String, val senderIsNewer: Boolean)

fun versionMismatch(senderVersion: String?, ourVersion: String? = CROC_VERSION): VersionMismatch? {
    val s = crocMinor(senderVersion) ?: return null
    val o = crocMinor(ourVersion) ?: return null
    if (s == o) return null
    val (sMaj, sMin) = s.split(".").map { it.toInt() }
    val (oMaj, oMin) = o.split(".").map { it.toInt() }
    return VersionMismatch(s, o, sMaj > oMaj || (sMaj == oMaj && sMin > oMin))
}

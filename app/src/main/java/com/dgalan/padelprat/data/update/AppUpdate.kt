package com.dgalan.padelprat.data.update

/** Última versión publicada en GitHub Releases, más nueva que la instalada. */
data class AppUpdate(
    val version: String,
    val apkUrl: String,
    val apkSize: Long,
    /** SHA-256 en hexadecimal que GitHub calcula del APK; `null` si no lo da. */
    val sha256: String?
)

/** Compara versiones `X.Y.Z` (con o sin `v` delante). Si alguna no se entiende, no se considera más nueva. */
internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val new = candidate.toVersionParts() ?: return false
    val old = current.toVersionParts() ?: return false
    for (i in 0 until maxOf(new.size, old.size)) {
        val a = new.getOrElse(i) { 0 }
        val b = old.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun String.toVersionParts(): List<Int>? = removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }

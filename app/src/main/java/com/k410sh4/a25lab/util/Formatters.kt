package com.k410sh4.a25lab.util

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    val digitGroup = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroup.toDouble())
    return String.format(Locale.US, "%.2f %s", value, units[digitGroup])
}

fun thermalStatusName(status: Int): String = when (status) {
    0 -> "Nenhum"
    1 -> "Leve"
    2 -> "Moderado"
    3 -> "Severo"
    4 -> "Crítico"
    5 -> "Emergência"
    6 -> "Desligamento"
    else -> "Desconhecido ($status)"
}


fun glEsVersionName(encodedVersion: Int): String {
    if (encodedVersion <= 0) return "N/D"
    val major = encodedVersion shr 16
    val minor = encodedVersion and 0xFFFF
    return "$major.$minor"
}

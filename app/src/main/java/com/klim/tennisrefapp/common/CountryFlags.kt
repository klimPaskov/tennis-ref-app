package com.klim.tennisrefapp.common

import java.util.Locale
import java.util.MissingResourceException

object CountryFlags {
    val iso3ToIso2Map: Map<String, String> by lazy { buildIso3ToIso2Map() }

    val iso2Codes: Set<String> by lazy {
        (Locale.getISOCountries().map { code -> code.uppercase(Locale.ROOT) } + iso3ToIso2Map.values).toSet()
    }

    fun toFlagEmoji(countryCode: String?): String? {
        val normalized = countryCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.ifBlank { null }
            ?: return null
        val iso2 = when {
            normalized.length == 2 && normalized in iso2Codes -> normalized
            normalized.length == 3 -> iso3ToIso2Map[normalized]
            else -> null
        } ?: return null
        if (iso2.length != 2 || !iso2.all { it in 'A'..'Z' }) return null
        val first = Character.codePointAt(iso2, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(iso2, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private fun buildIso3ToIso2Map(): Map<String, String> {
        val dynamic = mutableMapOf<String, String>()
        Locale.getISOCountries().forEach { iso2 ->
            val normalizedIso2 = iso2.uppercase(Locale.ROOT)
            val iso3 = try {
                Locale("", normalizedIso2).isO3Country.uppercase(Locale.ROOT)
            } catch (_: MissingResourceException) {
                null
            }
            if (iso3 != null && iso3.length == 3) {
                dynamic[iso3] = normalizedIso2
            }
        }
        dynamic.putAll(
            mapOf(
                "GBR" to "GB",
                "XKX" to "XK",
            ),
        )
        return dynamic.toMap()
    }
}

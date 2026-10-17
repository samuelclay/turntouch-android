package com.turntouch.android

import java.util.Locale

/** Match a preference list without mistaking a region for a Chinese script. */
internal object LanguageResolver {
    fun resolve(preferred: List<String>, supported: Set<String>): String {
        for (tag in preferred) {
            val locale=Locale.forLanguageTag(tag)
            if(locale.language=="zh")return if(locale.script=="Hant" || locale.country in listOf("TW","HK","MO"))"zh-Hant" else "zh-Hans"
            if(locale.language=="pt")return "pt-BR"
            if(tag in supported)return tag
            if(locale.language in supported)return locale.language
        }
        return "en"
    }
}

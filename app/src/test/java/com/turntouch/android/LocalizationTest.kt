package com.turntouch.android

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTest {
    private val supported=setOf("en","fr","pt-BR","zh-Hans","zh-Hant","ar","he")
    @Test fun respectsPreferredLanguageOrderAndFallsBackToEnglish() {
        assertEquals("fr",LanguageResolver.resolve(listOf("sv-SE","fr-CA","en-US"),supported))
        assertEquals("en",LanguageResolver.resolve(listOf("sv-SE"),supported))
        assertEquals("en",LanguageResolver.resolve(emptyList(),supported))
    }
    @Test fun matchesScriptsAndRegionalVariants() {
        assertEquals("zh-Hant",LanguageResolver.resolve(listOf("zh-HK"),supported))
        assertEquals("zh-Hant",LanguageResolver.resolve(listOf("zh-Hant-US"),supported))
        assertEquals("zh-Hans",LanguageResolver.resolve(listOf("zh-CN"),supported))
        assertEquals("pt-BR",LanguageResolver.resolve(listOf("pt-PT"),supported))
        assertEquals("he",LanguageResolver.resolve(listOf("iw-IL"),supported))
    }
    @Test fun substitutedDeviceNamesAreNeverReinterpretedAsPlaceholders() {
        assertEquals("Device {1}: ready",tr("Device {0}: {1}","{1}","ready"))
    }
}

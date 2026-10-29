package com.khaiit.kremote.feature.tuya

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TuyaOc09LabelRulesTest {
    @Test fun blankNameFallsBackToDefault() {
        val channel = TuyaChannel.ALL.first()
        assertEquals("Ổ cắm 1", TuyaOc09LabelRules.displayName(channel, "  "))
        assertEquals("Ổ cắm 1", TuyaOc09LabelRules.displayName(channel, null))
    }

    @Test fun customNameIsTrimmedWithoutTouchingCode() {
        val channel = TuyaChannel.ALL.first()
        assertEquals("switch_5", channel.code)
        assertEquals("Đèn phòng khách", TuyaOc09LabelRules.normalize("  Đèn phòng khách  "))
        assertEquals("Đèn phòng khách", TuyaOc09LabelRules.displayName(channel, " Đèn phòng khách "))
    }

    @Test fun longNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TuyaOc09LabelRules.normalize("a".repeat(TuyaOc09LabelRules.MAX_LENGTH + 1))
        }
    }

    @Test fun controlCharactersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TuyaOc09LabelRules.normalize("Thiết bị\nẩn")
        }
    }
}

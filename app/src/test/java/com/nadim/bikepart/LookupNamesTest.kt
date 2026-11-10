package com.nadim.bikepart

import com.nadim.bikepart.shop.LookupNames
import org.junit.Assert.assertEquals
import org.junit.Test

class LookupNamesTest {
    @Test fun lookupKeysUseBundledUnicodeDataWithoutAnAndroidRuntime() {
        // U+1E030 was unassigned in Unicode 14; Unicode 15 adds this mapping to Cyrillic a.
        assertEquals("а", LookupNames.key("  \uD838\uDC30  "))
        assertEquals("strasse café", LookupNames.key("Ｓtraße\u00a0Cafe\u0301"))
        assertEquals("οσ", LookupNames.key("ος"))
    }
}

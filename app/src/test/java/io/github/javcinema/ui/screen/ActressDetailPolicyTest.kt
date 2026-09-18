package io.github.javcinema.ui.screen

import com.google.gson.JsonParser
import io.github.javcinema.data.model.AvmooStar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActressDetailPolicyTest {

    @Test
    fun starDisplayNamePrefersPrimaryThenJa() {
        val star = AvmooStar(
            starId = "kwbydan",
            starDmmId = null,
            starName = null,
            starName_ja = "名前",
            starName_en = "Name",
            starName_cn = "中文",
            starName_tw = null,
            avatar = null,
            avatarUrl = null,
            movieCount = 12,
            weight = null,
            birthday = "1990-01-01",
            size = null
        )
        assertEquals("名前", starDisplayName(star))
    }

    @Test
    fun starSizeTextReadsPrimitive() {
        assertEquals("T170 B88", starSizeText(JsonParser.parseString("\"T170 B88\"")))
        assertNull(starSizeText(null))
    }
}

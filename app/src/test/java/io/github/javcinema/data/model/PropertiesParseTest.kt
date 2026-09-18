package io.github.javcinema.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertiesParseTest {

    @Test
    fun parseNumericVersionCodeAndSources() {
        val json = """
            {
              "latest_version": "0.1.0",
              "latest_version_code": 3,
              "changelog": "0.1.0",
              "data_sources": [
                { "name": "骑兵", "link": "https://avmoo.shop", "apiPath": "/jav/data/api/" }
              ],
              "magnet_sources": [
                { "name": "BtSearch", "link": "https://www.btsearch.love" }
              ]
            }
        """.trimIndent()

        val properties = Gson().fromJson(json, Properties::class.java)
        assertEquals("0.1.0", properties.latestVersion)
        assertEquals(3, properties.latestVersionCode)
        assertTrue(properties.dataSources?.isNotEmpty() == true)
        assertEquals("骑兵", properties.dataSources?.first()?.name)
    }
}

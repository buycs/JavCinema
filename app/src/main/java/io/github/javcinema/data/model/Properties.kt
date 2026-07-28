package io.github.javcinema.data.model

import com.google.gson.annotations.SerializedName
import io.github.javcinema.data.model.DataSource

/**
 * Project: JAViewer
 */
class Properties {

    @SerializedName("latest_version")
    var latestVersion: String? = null

    @SerializedName("latest_version_code")
    var latestVersionCode: Int = 0

    var changelog: String? = null

    @SerializedName("data_sources")
    var dataSources: List<DataSource>? = null

    @SerializedName("magnet_sources")
    var magnetSources: List<DataSource>? = null
}

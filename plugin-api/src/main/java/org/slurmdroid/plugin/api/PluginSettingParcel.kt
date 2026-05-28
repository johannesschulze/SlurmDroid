package org.slurmdroid.plugin.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Wire-safe representation of a plugin setting, passed over AIDL.
 * Use [PluginSetting] in plugin code and convert with [PluginSetting.toParcel].
 */
@Parcelize
data class PluginSettingParcel(
    val type: String,           // "text" | "toggle" | "dropdown"
    val key: String,
    val label: String,
    val defaultText: String,
    val defaultBool: Boolean,
    val options: List<String>,
) : Parcelable

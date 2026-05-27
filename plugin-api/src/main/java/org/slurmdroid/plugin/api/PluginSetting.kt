package org.slurmdroid.plugin.api

/** Kotlin-friendly sealed class for declaring plugin settings in [ISlurmDroidPlugin.getSettings]. */
sealed class PluginSetting {
    abstract val key: String
    abstract val label: String

    data class TextInput(
        override val key: String,
        override val label: String,
        val default: String = "",
    ) : PluginSetting()

    data class Toggle(
        override val key: String,
        override val label: String,
        val default: Boolean = false,
    ) : PluginSetting()

    data class Dropdown(
        override val key: String,
        override val label: String,
        val options: List<String>,
    ) : PluginSetting()

    fun toParcel(): PluginSettingParcel = when (this) {
        is TextInput -> PluginSettingParcel(
            type = "text", key = key, label = label,
            defaultText = default, defaultBool = false, options = emptyList(),
        )
        is Toggle -> PluginSettingParcel(
            type = "toggle", key = key, label = label,
            defaultText = "", defaultBool = default, options = emptyList(),
        )
        is Dropdown -> PluginSettingParcel(
            type = "dropdown", key = key, label = label,
            defaultText = options.firstOrNull() ?: "", defaultBool = false, options = options,
        )
    }
}

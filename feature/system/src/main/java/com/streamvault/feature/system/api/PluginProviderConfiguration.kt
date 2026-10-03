package com.streamvault.feature.system.api

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val pluginProviderJson = Json { ignoreUnknownKeys = true }

/** The order is intentional: a plugin advertising both transports contributes native Xtream. */
enum class PluginProviderCapability(val manifestCapability: String, val requestMessage: Int) {
    XTREAM(
        StreamVaultPluginContract.CAPABILITY_PROVIDER_XTREAM,
        StreamVaultPluginContract.MSG_GET_PROVIDER_CONFIGURATION
    ),
    M3U(
        StreamVaultPluginContract.CAPABILITY_PROVIDER_M3U,
        StreamVaultPluginContract.MSG_GET_PROVIDER_URL
    )
}

/** Untrusted Messenger transport data; never deserialize it directly into a persisted provider. */
@Serializable
data class PluginXtreamProviderConfiguration(
    val schemaVersion: Int,
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String
)

/** All failures are fixed strings so untrusted JSON and credentials cannot escape via diagnostics. */
fun parsePluginXtreamProviderConfiguration(
    providerType: String?,
    configurationJson: String?
): PluginXtreamProviderConfiguration {
    require(providerType == "xtream") { "Unsupported plugin provider type" }
    val payload = requireNotNull(configurationJson) { "Missing plugin provider configuration" }
    val configuration = try {
        pluginProviderJson.decodeFromString<PluginXtreamProviderConfiguration>(payload)
    } catch (_: SerializationException) {
        throw IllegalArgumentException("Malformed plugin provider configuration")
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Malformed plugin provider configuration")
    }
    require(configuration.schemaVersion == 1) { "Unsupported plugin provider configuration version" }
    require(configuration.name.isNotBlank()) { "Plugin provider name is required" }
    require(configuration.username.isNotBlank()) { "Plugin provider username is required" }
    require(configuration.password.isNotBlank()) { "Plugin provider password is required" }
    val serverUrl = configuration.serverUrl.trim()
    val uri = try {
        URI(serverUrl)
    } catch (_: Exception) {
        throw IllegalArgumentException("Invalid plugin provider server URL")
    }
    require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
    ) { "Invalid plugin provider server URL" }
    return configuration.copy(name = configuration.name.trim(), serverUrl = serverUrl)
}

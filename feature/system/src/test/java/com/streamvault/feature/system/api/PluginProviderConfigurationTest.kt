package com.streamvault.feature.system.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PluginProviderConfigurationTest {
    @Test fun `contract adds Xtream without changing existing messages`() {
        assertThat(StreamVaultPluginContract.CAPABILITY_PROVIDER_XTREAM).isEqualTo("provider.xtream")
        assertThat(listOf(
            StreamVaultPluginContract.MSG_GET_MANIFEST,
            StreamVaultPluginContract.MSG_SET_ENABLED,
            StreamVaultPluginContract.MSG_GET_STATUS,
            StreamVaultPluginContract.MSG_GET_PROVIDER_URL,
            StreamVaultPluginContract.MSG_PREPARE_PLAYBACK,
            StreamVaultPluginContract.MSG_REWRITE_CAST_URL,
            StreamVaultPluginContract.MSG_GET_CONFIGURATION_SCHEMA,
            StreamVaultPluginContract.MSG_GET_CONFIGURATION_VALUES,
            StreamVaultPluginContract.MSG_SET_CONFIGURATION_VALUES,
            StreamVaultPluginContract.MSG_RUN_CONFIGURATION_ACTION
        )).containsExactlyElementsIn(1..10).inOrder()
        assertThat(StreamVaultPluginContract.MSG_GET_PROVIDER_CONFIGURATION).isEqualTo(11)
    }

    @Test fun `provider transport routing is deterministic`() {
        fun manifest(vararg capabilities: String) = StreamVaultPluginManifest(
            id = "test", name = "Test", capabilities = capabilities.toList()
        )
        assertThat(manifest(StreamVaultPluginContract.CAPABILITY_PROVIDER_M3U).providerCapability)
            .isEqualTo(PluginProviderCapability.M3U)
        assertThat(PluginProviderCapability.M3U.requestMessage)
            .isEqualTo(StreamVaultPluginContract.MSG_GET_PROVIDER_URL)
        assertThat(manifest(StreamVaultPluginContract.CAPABILITY_PROVIDER_XTREAM).providerCapability)
            .isEqualTo(PluginProviderCapability.XTREAM)
        assertThat(PluginProviderCapability.XTREAM.requestMessage)
            .isEqualTo(StreamVaultPluginContract.MSG_GET_PROVIDER_CONFIGURATION)
        assertThat(manifest(
            StreamVaultPluginContract.CAPABILITY_PROVIDER_M3U,
            StreamVaultPluginContract.CAPABILITY_PROVIDER_XTREAM
        ).providerCapability).isEqualTo(PluginProviderCapability.XTREAM)
        assertThat(manifest().providerCapability).isNull()
    }

    @Test fun `valid Xtream configuration is typed and normalized`() {
        val parsed = parsePluginXtreamProviderConfiguration("xtream", validJson)
        assertThat(parsed.name).isEqualTo("Example Xtream")
        assertThat(parsed.serverUrl).isEqualTo("https://provider.example.com/base")
        assertThat(parsed.username).isEqualTo("user")
        assertThat(parsed.password).isEqualTo("secret")
    }

    @Test fun `invalid Xtream configurations are rejected without echoing secrets`() {
        val cases = listOf(
            "malformed" to "{",
            "missing name" to validJson.replace("\"name\":\" Example Xtream \"", "\"other\":\"value\""),
            "blank name" to validJson.replace(" Example Xtream ", "  "),
            "blank URL" to validJson.replace("https://provider.example.com/base", "  "),
            "malformed URL" to validJson.replace("https://provider.example.com/base", "http://%"),
            "unsupported scheme" to validJson.replace("https://provider.example.com/base", "ftp://provider.example.com"),
            "blank username" to validJson.replace("\"username\":\"user\"", "\"username\":\" \""),
            "blank password" to validJson.replace("\"password\":\"secret\"", "\"password\":\" \""),
            "wrong version" to validJson.replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        )
        cases.forEach { (_, payload) ->
            val failure = runCatching { parsePluginXtreamProviderConfiguration("xtream", payload) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(failure?.message).doesNotContain("secret")
        }
        assertThat(runCatching { parsePluginXtreamProviderConfiguration("m3u", validJson) }.isFailure).isTrue()
        assertThat(runCatching { parsePluginXtreamProviderConfiguration(null, validJson) }.isFailure).isTrue()
        assertThat(runCatching { parsePluginXtreamProviderConfiguration("xtream", null) }.isFailure).isTrue()
    }

    private val validJson = """{"schemaVersion":1,"name":" Example Xtream ","serverUrl":"https://provider.example.com/base","username":"user","password":"secret"}"""
}

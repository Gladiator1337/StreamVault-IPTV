package com.streamvault.app.plugins

import android.os.Build
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.streamvault.app.tvinput.TvInputChannelSyncManager
import com.streamvault.data.local.dao.PluginProviderOwnershipDao
import com.streamvault.data.local.entity.PluginProviderOwnershipEntity
import com.streamvault.domain.model.LegacyProvider
import com.streamvault.domain.model.M3uConfig
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.XtreamConfig
import com.streamvault.domain.repository.CombinedM3uRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.repository.ProviderSetupRequest
import com.streamvault.feature.system.api.InstalledStreamVaultPlugin
import com.streamvault.feature.system.api.StreamVaultPluginContract as Contract
import com.streamvault.feature.system.api.StreamVaultPluginManifest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class StreamVaultXtreamPluginManagerTest {
    private val messenger = mock<PluginMessengerClient>()
    private val repository = mock<ProviderRepository>()
    private val ownershipDao = mock<PluginProviderOwnershipDao>()
    private val combined = mock<CombinedM3uRepository>()
    private val tvInput = mock<TvInputChannelSyncManager>()
    private val work = mock<PluginWorkCoordinator>()
    private val provider = LegacyProvider(
        id = 42L, name = "Example", type = ProviderType.XTREAM_CODES,
        serverUrl = "https://example.test"
    )
    private val plugin = InstalledStreamVaultPlugin(
        packageName = "com.example.plugin",
        serviceClassName = "com.example.plugin.Service",
        appLabel = "Example",
        manifest = StreamVaultPluginManifest(
            id = "example", name = "Example",
            capabilities = listOf(Contract.CAPABILITY_PROVIDER_XTREAM)
        ),
        enabled = false
    )
    private lateinit var manager: StreamVaultPluginManager

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("streamvault_plugins", 0).edit().clear().commit()
        manager = StreamVaultPluginManager(
            context, messenger, repository, ownershipDao, combined, tvInput, work,
            mock<OkHttpClient>(), Json { ignoreUnknownKeys = true }
        )
        whenever(combined.getActiveLiveSource()).thenReturn(flowOf(null))
        wheneverBlocking { ownershipDao.get(any(), any(), any()) }.thenReturn(null)
        wheneverBlocking { ownershipDao.getByComponent(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { repository.setupProvider(any(), any(), isNull()) }
            .thenReturn(Result.success(provider))
        wheneverBlocking { messenger.send(any(), any(), any(), any(), any()) }.thenAnswer { call ->
            when (call.getArgument<Int>(2)) {
                Contract.MSG_SET_ENABLED -> Bundle().apply { putBoolean(Contract.KEY_SUCCESS, true) }
                Contract.MSG_GET_PROVIDER_CONFIGURATION -> Bundle().apply {
                    putBoolean(Contract.KEY_SUCCESS, true)
                    putString(Contract.KEY_PROVIDER_TYPE, "xtream")
                    putString(Contract.KEY_PROVIDER_CONFIGURATION_JSON, CONFIGURATION)
                }
                else -> error("Unexpected IPC message")
            }
        }
    }

    @Test fun `enable creates native Xtream provider and persists ownership`() = runBlocking {
        assertThat(manager.setPluginEnabled(plugin, true).success).isTrue()
        val requests = argumentCaptor<ProviderSetupRequest>()
        verifyBlocking(repository) { setupProvider(requests.capture(), any(), isNull()) }
        val request = requests.firstValue as ProviderSetupRequest.Configured
        assertThat(request.existingProviderId).isNull()
        assertThat(request.configuration).isInstanceOf(XtreamConfig::class.java)
        assertThat(request.configuration.type).isEqualTo(ProviderType.XTREAM_CODES)
        val owners = argumentCaptor<PluginProviderOwnershipEntity>()
        verifyBlocking(ownershipDao) { upsert(owners.capture()) }
        assertThat(owners.firstValue.providerId).isEqualTo(42L)
        assertThat(owners.firstValue.manifestId).isEqualTo("example")
        verifyBlocking(messenger) {
            send(eq(plugin.packageName), eq(plugin.serviceClassName), eq(Contract.MSG_GET_PROVIDER_CONFIGURATION), any(), any())
        }
    }

    @Test fun `re-enable updates the owned provider in place`() = runBlocking {
        val ownership = PluginProviderOwnershipEntity(
            plugin.packageName, plugin.serviceClassName, plugin.manifest.id, provider.id
        )
        wheneverBlocking { ownershipDao.get(any(), any(), any()) }.thenReturn(null, ownership)
        wheneverBlocking { repository.getProvider(provider.id) }.thenReturn(provider)
        assertThat(manager.setPluginEnabled(plugin, true).success).isTrue()
        assertThat(manager.setPluginEnabled(plugin, true).success).isTrue()
        val requests = argumentCaptor<ProviderSetupRequest>()
        verifyBlocking(repository, times(2)) { setupProvider(requests.capture(), any(), isNull()) }
        assertThat((requests.allValues[0] as ProviderSetupRequest.Configured).existingProviderId).isNull()
        assertThat((requests.allValues[1] as ProviderSetupRequest.Configured).existingProviderId).isEqualTo(42L)
    }

    @Test fun `invalid configuration does not replace an owned provider`() = runBlocking {
        wheneverBlocking { messenger.send(any(), any(), eq(Contract.MSG_GET_PROVIDER_CONFIGURATION), any(), any()) }
            .thenReturn(Bundle().apply {
                putBoolean(Contract.KEY_SUCCESS, true)
                putString(Contract.KEY_PROVIDER_TYPE, "xtream")
                putString(Contract.KEY_PROVIDER_CONFIGURATION_JSON, CONFIGURATION.replace("secret", " "))
            })
        val result = manager.setPluginEnabled(plugin, true)
        assertThat(result.success).isFalse()
        assertThat(result.message).doesNotContain("secret")
        verifyBlocking(repository, times(0)) { setupProvider(any(), any(), isNull()) }
    }

    @Test fun `disable removes the owned Xtream provider`() = runBlocking {
        val ownership = PluginProviderOwnershipEntity(
            plugin.packageName, plugin.serviceClassName, plugin.manifest.id, provider.id
        )
        wheneverBlocking { ownershipDao.get(any(), any(), any()) }.thenReturn(ownership)
        wheneverBlocking { repository.deleteProvider(eq(provider.id), isNull()) }
            .thenReturn(Result.success(com.streamvault.domain.repository.ProviderDeleteOutcome(provider.id, 0, false)))
        assertThat(manager.setPluginEnabled(plugin, false).success).isTrue()
        verifyBlocking(repository) { deleteProvider(eq(provider.id), isNull()) }
        verifyBlocking(ownershipDao) {
            delete(eq(plugin.packageName), eq(plugin.serviceClassName), eq(plugin.manifest.id))
        }
    }

    @Test fun `M3U plugins still use the original URL message and setup path`() = runBlocking {
        val m3uPlugin = plugin.copy(manifest = plugin.manifest.copy(
            capabilities = listOf(Contract.CAPABILITY_PROVIDER_M3U)
        ))
        val m3uProvider = provider.copy(type = ProviderType.M3U, serverUrl = "https://example.test/list.m3u")
        wheneverBlocking { repository.setupProvider(any(), any(), isNull()) }
            .thenReturn(Result.success(m3uProvider))
        wheneverBlocking { messenger.send(any(), any(), eq(Contract.MSG_GET_PROVIDER_URL), any(), any()) }
            .thenReturn(Bundle().apply {
                putBoolean(Contract.KEY_SUCCESS, true)
                putString(Contract.KEY_URL, "https://example.test/list.m3u")
            })
        assertThat(manager.setPluginEnabled(m3uPlugin, true).success).isTrue()
        val requests = argumentCaptor<ProviderSetupRequest>()
        verifyBlocking(repository) { setupProvider(requests.capture(), any(), isNull()) }
        assertThat((requests.firstValue as ProviderSetupRequest.Configured).configuration)
            .isInstanceOf(M3uConfig::class.java)
        verifyBlocking(messenger) {
            send(eq(plugin.packageName), eq(plugin.serviceClassName), eq(Contract.MSG_GET_PROVIDER_URL), any(), any())
        }
    }

    private companion object {
        const val CONFIGURATION = """{"schemaVersion":1,"name":"Example","serverUrl":"https://example.test","username":"user","password":"secret"}"""
    }
}

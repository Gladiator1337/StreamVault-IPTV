package com.streamvault.app.plugins

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.dao.PluginProviderOwnershipDao
import com.streamvault.data.local.entity.PluginProviderOwnershipEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RoomPluginProviderOwnershipReaderTest {
    private val dao = mock<PluginProviderOwnershipDao>()
    private val reader = RoomPluginProviderOwnershipReader(dao)
    private val ownership = PluginProviderOwnershipEntity(
        packageName = "com.streamvault.providerremote",
        serviceClassName = "RemoteService",
        manifestId = "remote",
        providerId = 42L
    )

    @Test fun `ownership flow marks plugin provider by ID only`() = runTest {
        whenever(dao.observeAll()).thenReturn(flowOf(listOf(ownership)))

        val owners = reader.observeOwners().first()

        assertThat(owners.keys).containsExactly(42L)
        assertThat(owners[42L]?.packageName).isEqualTo("com.streamvault.providerremote")
        assertThat(owners[7L]).isNull()
    }

    @Test fun `lookup uses indexed provider ID`() = runTest {
        whenever(dao.getByProviderId(42L)).thenReturn(ownership)

        assertThat(reader.ownerOf(42L)?.manifestId).isEqualTo("remote")
        assertThat(reader.ownerOf(7L)).isNull()
    }
}

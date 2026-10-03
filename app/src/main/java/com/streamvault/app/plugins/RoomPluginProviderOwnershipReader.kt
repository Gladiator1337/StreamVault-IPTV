package com.streamvault.app.plugins

import com.streamvault.data.local.dao.PluginProviderOwnershipDao
import com.streamvault.data.local.entity.PluginProviderOwnershipEntity
import com.streamvault.domain.provider.PluginProviderOwnershipReader
import com.streamvault.domain.provider.PluginSourceIdentity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPluginProviderOwnershipReader @Inject constructor(
    private val ownershipDao: PluginProviderOwnershipDao
) : PluginProviderOwnershipReader {
    override fun observeOwners(): Flow<Map<Long, PluginSourceIdentity>> =
        ownershipDao.observeAll().map { rows -> rows.associate { it.providerId to it.identity() } }

    override suspend fun ownerOf(providerId: Long): PluginSourceIdentity? =
        ownershipDao.getByProviderId(providerId)?.identity()

    private fun PluginProviderOwnershipEntity.identity() = PluginSourceIdentity(
        packageName = packageName,
        serviceClassName = serviceClassName,
        manifestId = manifestId
    )
}

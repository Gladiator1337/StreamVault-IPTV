package com.streamvault.domain.provider

import kotlinx.coroutines.flow.Flow

/** Read-only ownership boundary for user-facing provider management. */
interface PluginProviderOwnershipReader {
    fun observeOwners(): Flow<Map<Long, PluginSourceIdentity>>

    suspend fun ownerOf(providerId: Long): PluginSourceIdentity?
}

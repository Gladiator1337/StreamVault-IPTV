package com.streamvault.feature.settings.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderActionAvailabilityTest {
    @Test fun `manual provider keeps edit and delete`() {
        val actions = providerActionAvailability(isPluginManaged = false, isActive = true, liveOnboardingIncomplete = false)
        assertThat(actions.allowEditAndDelete).isTrue()
        assertThat(actions.showCategoryControls).isTrue()
    }

    @Test fun `plugin provider hides edit and delete but keeps sync and categories`() {
        val active = providerActionAvailability(isPluginManaged = true, isActive = true, liveOnboardingIncomplete = false)
        assertThat(active.allowEditAndDelete).isFalse()
        assertThat(active.showCategoryControls).isTrue()

        val inactive = providerActionAvailability(isPluginManaged = true, isActive = false, liveOnboardingIncomplete = false)
        assertThat(inactive.allowEditAndDelete).isFalse()
        assertThat(inactive.showDedicatedSync).isTrue()
        assertThat(inactive.showCategoryControls).isTrue()
    }
}

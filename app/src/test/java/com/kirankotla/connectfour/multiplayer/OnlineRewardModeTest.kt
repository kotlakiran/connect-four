package com.kirankotla.connectfour.multiplayer

import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.model.OnlineGameDoc
import com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc
import com.kirankotla.connectfour.multiplayer.model.allowedRewardModesFor
import com.kirankotla.connectfour.multiplayer.model.normalizeOnlineRewardMode
import com.kirankotla.connectfour.multiplayer.model.resolveRewardModeForGame
import com.kirankotla.connectfour.multiplayer.repository.NearbyMultiplayerRepository
import com.kirankotla.connectfour.multiplayer.repository.SimulatedMultiplayerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRewardModeTest {
    @Test
    fun defaultsToRanked() {
        assertEquals("RANKED", OnlineGameDoc().rewardMode)
        assertEquals("RANKED", normalizeOnlineRewardMode(null))
        assertEquals("RANKED", normalizeOnlineRewardMode("casual"))
    }

    @Test
    fun keepsExplicitRewardModes() {
        assertEquals("CASUAL", normalizeOnlineRewardMode("CASUAL"))
        assertEquals("POWER_UP", normalizeOnlineRewardMode("POWER_UP"))
    }

    @Test
    fun powerUpIsNotOfferedForQuickMatch() {
        assertFalse("POWER_UP" in allowedRewardModesFor("QUICK_MATCH"))
        assertTrue("POWER_UP" in allowedRewardModesFor("PRIVATE_ROOM"))
        assertTrue("POWER_UP" in allowedRewardModesFor("OFFLINE_NEARBY"))
        assertEquals("CASUAL", resolveRewardModeForGame("QUICK_MATCH", "POWER_UP"))
        assertEquals("POWER_UP", resolveRewardModeForGame("PRIVATE_ROOM", "POWER_UP"))
        assertEquals("RANKED", resolveRewardModeForGame("QUICK_MATCH", null))
    }

    @Test
    fun nearbyEndpointCarriesRewardModeWithLegacyDefault() {
        val name = NearbyMultiplayerRepository.encodeEndpointName("Ann", "1234", BoardSpec.WIDE_3X6, 15, "POWER_UP")
        val parts = name.split(NearbyMultiplayerRepository.NAME_SEPARATOR)
        assertEquals("POWER_UP", NearbyMultiplayerRepository.parseEndpointRewardMode(parts))
        assertEquals("RANKED", NearbyMultiplayerRepository.parseEndpointRewardMode(listOf("Ann", "1234", "3")))
        assertEquals("RANKED", NearbyMultiplayerRepository.parseEndpointRewardMode(listOf("Ann", "1234", "3", "3", "x", "15", "bogus")))
    }

    @Test
    fun simulatedRepoStoresClampedRewardMode() = runBlocking {
        val player = OnlinePlayerDoc(uid = "p1", displayName = "P")
        val repo = SimulatedMultiplayerRepository()
        repo.startQuickMatch(player, BoardSpec.CLASSIC_3X3, 15, "POWER_UP")
        assertEquals("CASUAL", repo.currentSession.value?.rewardMode)
        repo.createPrivateGame(player, BoardSpec.CLASSIC_3X3, 15, "POWER_UP")
        assertEquals("POWER_UP", repo.currentSession.value?.rewardMode)
        repo.createPrivateGame(player, BoardSpec.CLASSIC_3X3)
        assertEquals("RANKED", repo.currentSession.value?.rewardMode)
    }
}

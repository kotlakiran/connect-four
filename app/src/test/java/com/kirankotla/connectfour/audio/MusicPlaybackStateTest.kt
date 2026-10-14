package com.kirankotla.connectfour.audio

import com.kirankotla.connectfour.util.MusicPlaybackPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlaybackStateTest {

    @Test
    fun testMusicOnlyPlaysWhenForegroundAndEnabledAndRequested() {
        // App in foreground, user enabled music, game started music -> allowed
        assertTrue(
            "Music should play when foreground, enabled, and requested",
            MusicPlaybackPolicy.shouldPlayAudio(
                isForeground = true,
                isMusicEnabled = true,
                shouldPlay = true
            )
        )
    }

    @Test
    fun testMusicPausesWhenMovedToBackground() {
        // When app is in background (isForeground = false), music MUST NOT play regardless of other flags
        assertFalse(
            "Music MUST pause when app is moved to background",
            MusicPlaybackPolicy.shouldPlayAudio(
                isForeground = false,
                isMusicEnabled = true,
                shouldPlay = true
            )
        )
    }

    @Test
    fun testMusicDoesNotPlayWhenDisabledInSettings() {
        assertFalse(
            "Music must not play if user disabled it in settings",
            MusicPlaybackPolicy.shouldPlayAudio(
                isForeground = true,
                isMusicEnabled = false,
                shouldPlay = true
            )
        )
    }

    @Test
    fun testMusicDoesNotPlayWhenExplicitlyStopped() {
        assertFalse(
            "Music must not play if not requested or explicitly stopped",
            MusicPlaybackPolicy.shouldPlayAudio(
                isForeground = true,
                isMusicEnabled = true,
                shouldPlay = false
            )
        )
    }

    @Test
    fun testMusicNeverPlaysWhenBackgroundedEvenIfPreparedLater() {
        // Simulates async media preparation completing while app is in background
        assertFalse(
            "Music must not start if preparation finishes while backgrounded",
            MusicPlaybackPolicy.shouldPlayAudio(
                isForeground = false,
                isMusicEnabled = true,
                shouldPlay = true
            )
        )
    }
}

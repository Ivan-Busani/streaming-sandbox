package com.mivan.streamingsandbox.feature.player.domain

interface PlayerEngineFactory {
    fun create(vendor: PlayerVendor): PlayerEngine
}
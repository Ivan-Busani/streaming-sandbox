package com.mivan.streamingsandbox.feature.player.data

import com.mivan.streamingsandbox.feature.player.domain.PlayerEngine
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineFactory
import com.mivan.streamingsandbox.feature.player.domain.PlayerVendor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPlayerEngineFactory @Inject constructor(
    private val exoPlayerEngine: ExoPlayerEngine,
    private val bitmovinPlayerEngine: BitmovinPlayerEngine,
    private val castlabsPlayerEngine: CastlabsPlayerEngine
) : PlayerEngineFactory {

    override fun create(vendor: PlayerVendor): PlayerEngine {
        return when (vendor) {
            PlayerVendor.EXOPLAYER -> exoPlayerEngine
            PlayerVendor.BITMOVIN -> bitmovinPlayerEngine
            PlayerVendor.CASTLAB -> castlabsPlayerEngine
        }
    }
}
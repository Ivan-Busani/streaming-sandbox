package com.mivan.streamingsandbox.feature.player.data

import com.mivan.streamingsandbox.feature.player.domain.PlayerVendor
import com.mivan.streamingsandbox.feature.player.domain.PlayerVendorProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPlayerVendorProvider @Inject constructor() : PlayerVendorProvider {
    override fun currentVendor(): PlayerVendor = PlayerVendor.EXOPLAYER
}
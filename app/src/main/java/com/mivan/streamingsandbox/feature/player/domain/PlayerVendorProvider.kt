package com.mivan.streamingsandbox.feature.player.domain

interface PlayerVendorProvider {
    fun currentVendor(): PlayerVendor
}
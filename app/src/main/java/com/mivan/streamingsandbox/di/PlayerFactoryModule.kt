package com.mivan.streamingsandbox.di

import com.mivan.streamingsandbox.feature.player.data.DefaultPlayerEngineFactory
import com.mivan.streamingsandbox.feature.player.data.DefaultPlayerVendorProvider
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineFactory
import com.mivan.streamingsandbox.feature.player.domain.PlayerVendorProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerFactoryModule {

    @Binds
    @Singleton
    abstract fun bindPlayerEngineFactory(
        impl: DefaultPlayerEngineFactory
    ): PlayerEngineFactory

    @Binds
    @Singleton
    abstract fun bindPlayerVendorProvider(
        impl: DefaultPlayerVendorProvider
    ): PlayerVendorProvider
}
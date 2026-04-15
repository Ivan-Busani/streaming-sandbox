package com.mivan.streamingsandbox.di

import com.mivan.streamingsandbox.feature.channels.data.repository.ChannelRepositoryImpl
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChannelRepository(
        impl: ChannelRepositoryImpl
    ): ChannelRepository
}
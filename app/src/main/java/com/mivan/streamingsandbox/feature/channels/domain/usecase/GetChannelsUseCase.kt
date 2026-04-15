package com.mivan.streamingsandbox.feature.channels.domain.usecase

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import javax.inject.Inject

class GetChannelsUseCase @Inject constructor(
    private val repository: ChannelRepository
) {
    operator fun invoke(): List<Channel> = repository.getChannels()
}

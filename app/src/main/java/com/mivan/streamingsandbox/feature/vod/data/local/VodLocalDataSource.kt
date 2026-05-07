package com.mivan.streamingsandbox.feature.vod.data.local

import android.content.Context
import com.mivan.streamingsandbox.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class VodLocalDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext: Context = context

    fun loadCatalogJson(): String {
        return appContext.resources
            .openRawResource(R.raw.vod_catalog)
            .bufferedReader()
            .use { it.readText() }
    }
}

package com.nuvio.tv.core.di

import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import com.nuvio.tv.data.local.IntroDetectionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class IntroDetectionModule {
    @Binds
    @IntoSet
    abstract fun introDetectionCredentials(store: IntroDetectionStore): ProfileScopedCredentialStore
}

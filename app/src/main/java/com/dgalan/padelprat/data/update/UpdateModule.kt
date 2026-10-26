package com.dgalan.padelprat.data.update

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Cliente para GitHub: sin las cookies ni los interceptores de la web del club, y siguiendo redirecciones. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    @GitHubClient
    fun provideGitHubClient(): OkHttpClient = OkHttpClient.Builder()
        // La descarga del APK redirige a otro dominio de GitHub
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

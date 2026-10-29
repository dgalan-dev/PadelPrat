package com.dgalan.padelprat.data.friends

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Colecciones de Firestore; las reglas están en firestore.rules (ver docs/plan-reservas-amigo.md). */
internal object FirestoreCollections {
    /** Quién puede usar las reservas compartidas: solo se da de alta desde la consola. */
    const val MEMBERS = "members"

    /** Las próximas reservas que publica cada uno, en el documento de su uid. */
    const val RESERVATIONS = "reservations"
}

/** Cliente para enviar las push de FCM: sin las cookies ni los interceptores de la web del club, y sin logs. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FcmClient

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    @FcmClient
    fun provideFcmClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager =
        CredentialManager.create(context)
}

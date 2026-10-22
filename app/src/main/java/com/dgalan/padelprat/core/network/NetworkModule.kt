package com.dgalan.padelprat.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.dgalan.padelprat.BuildConfig
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Nombres de los ficheros de la sesión; están excluidos de las copias de seguridad (res/xml)
    private const val COOKIES_FILE = "session_cookies.bin"
    private const val KEYSET_NAME = "session_keyset"
    private const val KEYSET_PREFS = "session_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://padelprat_session_master_key"

    @Provides
    @Singleton
    fun provideBaseUrl(): HttpUrl = BuildConfig.BASE_URL.toHttpUrl()

    /** Cifrado de la sesión: la clave de Tink se guarda protegida por una clave maestra del Keystore de Android. */
    @Provides
    @Singleton
    fun provideAead(@ApplicationContext context: Context): Aead {
        AeadConfig.register()
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    @Provides
    @Singleton
    fun provideCookieDataStore(@ApplicationContext context: Context, aead: Aead): DataStore<List<Cookie>> =
        DataStoreFactory.create(
            serializer = CookieSerializer(aead),
            produceFile = { context.dataStoreFile(COOKIES_FILE) }
        )

    @Provides
    @Singleton
    fun provideOkHttpClient(
        baseUrl: HttpUrl,
        cookieJar: PersistentCookieJar,
        sessionExpiredNotifier: SessionExpiredNotifier
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        // Las redirecciones se tratan a mano: un 303 al login significa sesión caducada
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(BrowserHeadersInterceptor(baseUrl))
        .addInterceptor(SessionExpiredInterceptor(cookieJar, sessionExpiredNotifier))
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC: solo método, URL y código. Nunca cabeceras ni cuerpos (cookies y contraseña)
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

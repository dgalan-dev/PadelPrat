package com.dgalan.padelprat.data.friends

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.dgalan.padelprat.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

sealed interface GoogleSignInResult {
    data object Success : GoogleSignInResult

    /** El usuario ha cerrado el selector de cuentas. */
    data object Cancelled : GoogleSignInResult

    /** No hay ninguna cuenta de Google en el móvil. */
    data object NoAccount : GoogleSignInResult

    data object NetworkError : GoogleSignInResult

    data object UnknownError : GoogleSignInResult
}

sealed interface Membership {
    data object SignedOut : Membership

    /** Con sesión de Google, pero sin alta en `members` (se hace desde la consola). */
    data object NotMember : Membership

    data class Member(val name: String?) : Membership

    /** Sin conexión y sin el dato en la caché de Firestore. */
    data object Unknown : Membership
}

/**
 * Sesión de Firebase para las reservas compartidas, con la cuenta de Google del usuario. Es independiente de la
 * sesión del club y opcional: sin ella la app funciona igual.
 */
@Singleton
class FriendAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val credentialManager: CredentialManager,
    private val sharedReservations: SharedReservationsRepository,
    private val friendAlerts: FriendAlertsRepository
) {

    val isSignedIn: Boolean get() = auth.currentUser != null

    val email: String? get() = auth.currentUser?.email

    /** [activityContext] tiene que ser una Activity: Credential Manager muestra encima el selector de cuentas. */
    suspend fun signIn(activityContext: Context): GoogleSignInResult {
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (_: GetCredentialCancellationException) {
            return GoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            return GoogleSignInResult.NoAccount
        } catch (_: GetCredentialException) {
            return GoogleSignInResult.UnknownError
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleSignInResult.UnknownError
        }
        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (_: GoogleIdTokenParsingException) {
            return GoogleSignInResult.UnknownError
        }
        return try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            GoogleSignInResult.Success
        } catch (_: FirebaseNetworkException) {
            GoogleSignInResult.NetworkError
        } catch (_: FirebaseException) {
            GoogleSignInResult.UnknownError
        }
    }

    /** Las reglas solo dejan leer `members` a los miembros: a quien no lo es, Firestore le responde sin permiso. */
    suspend fun membership(): Membership {
        val user = auth.currentUser ?: return Membership.SignedOut
        return try {
            val member = firestore.collection(FirestoreCollections.MEMBERS).document(user.uid).get().await()
            if (member.exists()) Membership.Member(member.getString("name")) else Membership.NotMember
        } catch (e: FirebaseFirestoreException) {
            if (e.code ==
                FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                Membership.NotMember
            } else {
                Membership.Unknown
            }
        }
    }

    /**
     * Borra antes lo publicado (con la sesión todavía abierta, que es lo que piden las reglas) y olvida la cuenta
     * elegida, para que la próxima vez se pueda elegir otra.
     */
    suspend fun signOut() {
        sharedReservations.unpublish()
        friendAlerts.clear()
        auth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            // Solo afecta a qué cuenta se propone la próxima vez
        }
    }
}

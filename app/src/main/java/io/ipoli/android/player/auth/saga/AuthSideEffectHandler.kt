package io.ipoli.android.player.auth.saga

import android.annotation.SuppressLint
import com.google.firebase.auth.*
import io.ipoli.android.BuildConfig
import io.ipoli.android.Constants
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.LoadDataAction
import io.ipoli.android.common.api.Api
import io.ipoli.android.common.redux.Action
import io.ipoli.android.player.AuthProvider
import io.ipoli.android.player.Player
import io.ipoli.android.player.auth.AuthAction
import io.ipoli.android.player.auth.AuthViewState
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.tag.Tag
import space.traversal.kapsule.required
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/07/2018.
 */
object AuthSideEffectHandler : AppSideEffectHandler() {

    private val eventLogger by required { eventLogger }
    private val playerRepository by required { playerRepository }
    private val tagRepository by required { tagRepository }
    private val sharedPreferences by required { sharedPreferences }
    private val petStatsChangeScheduler by required { lowerPetStatsScheduler }
    private val saveQuestsForRepeatingQuestScheduler by required { saveQuestsForRepeatingQuestScheduler }
    private val removeExpiredPowerUpsScheduler by required { removeExpiredPowerUpsScheduler }
    private val checkMembershipStatusScheduler by required { checkMembershipStatusScheduler }

    override fun canHandle(action: Action) = action is AuthAction

    override suspend fun doExecute(action: Action, state: AppState) {

        when (action) {
            AuthAction.Load -> {
                val hasPlayer = playerRepository.hasPlayer()
                var isGuest = false
                var hasUsername = false
                if (hasPlayer) {
                    val player = playerRepository.find()
                    isGuest = player!!.authProvider is AuthProvider.Guest
                    hasUsername = !player.username.isNullOrEmpty()
                }
                dispatch(AuthAction.Loaded(hasPlayer, isGuest, hasUsername))
            }

            is AuthAction.UserAuthenticated -> {
                val user = action.user

                val metadata = user.metadata
                val isNewUser =
                    metadata == null || metadata.creationTimestamp == metadata.lastSignInTimestamp
                val hasPlayer = playerRepository.hasPlayer()
                when {
                    !isNewUser && hasPlayer -> {
                        //TODO: delete anonymous account
                        val anonymousPlayerId =
                            sharedPreferences.getString(Constants.KEY_PLAYER_ID, null)
                        savePlayerId(user)
                        dispatch(LoadDataAction.ChangePlayer(anonymousPlayerId))
                        dispatch(AuthAction.ExistingPlayerLoggedInFromGuest)
                    }
                    isNewUser && hasPlayer -> {
                        updatePlayerAuthProvider(user)
                        dispatch(AuthAction.AccountsLinked)
                    }
                    isNewUser && !hasPlayer -> {
                        createNewPlayer(user)
                    }
                    else -> loginExistingPlayer(user)
                }
            }

            is AuthAction.CompleteSetup -> {

                val username = action.username

                val usernameValidationError =
                    findUsernameValidationError(username, playerRepository)

                if (usernameValidationError != null) {
                    dispatch(
                        AuthAction.UsernameValidationFailed(
                            usernameValidationError
                        )
                    )
                } else {

                    val player = playerRepository.find()!!
                    playerRepository.save(
                        player.copy(
                            username = action.username,
                            avatar = action.avatar
                        )
                    )
                    playerRepository.addUsername(action.username)
                    prepareAppStart()
                    dispatch(AuthAction.PlayerSetupCompleted)

                    if (!BuildConfig.DEBUG) {
                        val auth = player.authProvider
                        if (auth is AuthProvider.Facebook && auth.email != null) {
                            Api.migratePlayer(player.id, auth.email)
                        } else if (auth is AuthProvider.Google && auth.email != null) {
                            Api.migratePlayer(player.id, auth.email)
                        }
                    }
                }
            }

            is AuthAction.ValidateUsername -> {
                val username = action.username

                val usernameValidationError =
                    findUsernameValidationError(username, playerRepository)
                if (usernameValidationError != null) {
                    dispatch(
                        AuthAction.UsernameValidationFailed(
                            usernameValidationError
                        )
                    )
                } else {
                    dispatch(AuthAction.UsernameValid)
                }
            }
        }
    }

    private suspend fun updatePlayerAuthProvider(
        user: FirebaseUser
    ) {
        val authProviders =
            user.providerData.filter { it.providerId != FirebaseAuthProvider.PROVIDER_ID }
        require(authProviders.size == 1)
        val authProvider = authProviders.first()

        val auth = when {
            authProvider.providerId == FacebookAuthProvider.PROVIDER_ID ->
                createFacebookAuthProvider(
                    authProvider,
                    user
                )
            authProvider.providerId == GoogleAuthProvider.PROVIDER_ID ->
                createGoogleAuthProvider(
                    authProvider,
                    user
                )
            else -> throw IllegalStateException("Unknown Auth provider")
        }

        val player = playerRepository.find()
        playerRepository.save(
            player!!.copy(
                authProvider = auth
            )
        )
    }

    private fun createGoogleAuthProvider(
        authProvider: UserInfo,
        user: FirebaseUser
    ) =
        AuthProvider.Google(
            userId = authProvider.uid,
            displayName = user.displayName!!,
            email = user.email!!,
            imageUrl = user.photoUrl
        )

    private fun createFacebookAuthProvider(
        authProvider: UserInfo,
        user: FirebaseUser
    ) =
        AuthProvider.Facebook(
            userId = authProvider.uid,
            displayName = user.displayName!!,
            email = user.email!!,
            imageUrl = user.photoUrl
        )

    private fun createNewPlayer(
        user: FirebaseUser
    ) {

        val authProvider = if (user.providerData.size == 1) {
            user.providerData.first()
        } else {
            val authProviders =
                user.providerData.filter { it.providerId != FirebaseAuthProvider.PROVIDER_ID }
            require(authProviders.size == 1)
            authProviders.first()
        }

        val auth = when {

            authProvider.providerId == FacebookAuthProvider.PROVIDER_ID ->
                createFacebookAuthProvider(authProvider, user)

            authProvider.providerId == GoogleAuthProvider.PROVIDER_ID ->
                createGoogleAuthProvider(authProvider, user)

            authProvider.providerId == FirebaseAuthProvider.PROVIDER_ID ->
                AuthProvider.Guest(authProvider.uid)

            else -> throw IllegalStateException("Unknown Auth provider")
        }

        val player = Player(
            authProvider = auth,
            username = null,
            displayName = if (user.displayName != null) user.displayName!! else "",
            schemaVersion = Constants.SCHEMA_VERSION
        )

        playerRepository.create(player, user.uid)
        savePlayerId(user)

        saveDefaultTags()

        if (auth is AuthProvider.Guest) {
            prepareAppStart()
            dispatch(AuthAction.GuestCreated)
        } else {
            dispatch(AuthAction.ShowSetUp)
        }
    }

    private fun saveDefaultTags() {
        tagRepository.save(
            listOf(
                Tag(
                    name = "Personal",
                    color = Color.ORANGE,
                    icon = Icon.DUCK,
                    isFavorite = true
                ),
                Tag(
                    name = "Work",
                    color = Color.RED,
                    icon = Icon.BRIEFCASE,
                    isFavorite = true
                ),
                Tag(
                    name = "Wellness",
                    color = Color.GREEN,
                    icon = Icon.FLOWER,
                    isFavorite = true
                )
            )
        )
    }

    private fun loginExistingPlayer(user: FirebaseUser) {
        savePlayerId(user)
        dispatch(AuthAction.PlayerLoggedIn)
        prepareAppStart()
    }

    @SuppressLint("ApplySharedPref")
    private fun savePlayerId(user: FirebaseUser) {
        eventLogger.setPlayerId(user.uid)
        sharedPreferences.edit().putString(Constants.KEY_PLAYER_ID, user.uid).commit()
    }

    private fun prepareAppStart() {
        dispatch(LoadDataAction.All)
        petStatsChangeScheduler.schedule()
        saveQuestsForRepeatingQuestScheduler.schedule()
        removeExpiredPowerUpsScheduler.schedule()
        checkMembershipStatusScheduler.schedule()
    }

    private fun findUsernameValidationError(
        username: String,
        playerRepository: PlayerRepository
    ): AuthViewState.ValidationError? {

        if (username.isBlank()) {
            return AuthViewState.ValidationError.EMPTY_USERNAME
        }

        val asciiEncoder = Charset.forName("US-ASCII").newEncoder()
        if (!asciiEncoder.canEncode(username)) {
            return AuthViewState.ValidationError.INVALID_FORMAT
        }

        val p = Pattern.compile("^\\w+$")
        if (!p.matcher(username).matches()) {
            return AuthViewState.ValidationError.INVALID_FORMAT
        }

        if (username.length < Constants.USERNAME_MIN_LENGTH || username.length > Constants.USERNAME_MAX_LENGTH) {
            return AuthViewState.ValidationError.INVALID_LENGTH
        }

        if (!playerRepository.isUsernameAvailable(username)) {
            return AuthViewState.ValidationError.EXISTING_USERNAME
        }

        return null
    }
}
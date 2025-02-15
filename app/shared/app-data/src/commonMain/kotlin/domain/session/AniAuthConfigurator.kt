/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.models.fold
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.GuestSession
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Wrapper for [SessionManager] and [AniAuthClient] to handle authorization.
 * Usually use it at UI layer (e.g. at ViewModel).
 * 
 * You should call [authorizeRequestCheckLoop] to start the test runner loop and make functionality.
 */
class AniAuthConfigurator(
    private val sessionManager: SessionManager,
    private val authClient: AniAuthClient,
    private val onLaunchAuthorize: suspend (requestId: String) -> Unit,
    private val awaitRetryInterval: Duration = 1.seconds,
    parentCoroutineContext: CoroutineContext = Dispatchers.Default,
) {
    private val logger = logger<AniAuthConfigurator>()
    private val scope = parentCoroutineContext.childScope()

    private val authorizeTasker = MonoTasker(scope)
    private val currentRequestAuthorizeId = MutableStateFlow<String?>(null)

    val state: StateFlow<AuthStateNew> = currentRequestAuthorizeId
        .transformLatest { requestId ->
            if (requestId == null) {
                emit(AuthStateNew.Idle)
                logger.debug { "[AuthState] Got null request id, stopped checking." }
                return@transformLatest
            } else {
                emit(AuthStateNew.AwaitingResult(requestId))
            }

            logger.debug { "[AuthState][${requestId.idStr}] Start checking session state." }
            sessionManager.state
                .collectLatest { sessionState ->
                    // 如果有 token, 直接获取当前 session 的状态即可
                    if (sessionState !is SessionStatus.NoToken)
                        return@collectLatest collectCombinedAuthState(requestId, sessionState, null)

                    // 如果是 NoToken 并且还是 REFRESH, 则直接返回 Idle
                    if (requestId == REFRESH) {
                        logger.debug { "[AuthState][${requestId.idStr}] No existing session." }
                        emit(AuthStateNew.Idle)
                        return@collectLatest
                    }
                    
                    sessionManager.processingRequest
                        .transform { processingRequest ->
                            if (processingRequest == null) {
                                logger.debug { "[AuthState][${requestId.idStr}] No processing request." }
                                emit(null)
                            } else {
                                logger.debug { "[AuthState][${requestId.idStr}] Current processing request: $processingRequest" }
                                emitAll(processingRequest.state)
                            }
                        }
                        .collectLatest { requestState ->
                            collectCombinedAuthState(requestId, sessionState, requestState)
                        }
                }
        }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(),
            AuthStateNew.Idle,
        )

    suspend fun authorizeRequestCheckLoop() {
        processAuthorizeRequestTask()
    }
    
    private suspend fun processAuthorizeRequestTask() {
        currentRequestAuthorizeId
            .filterNotNull()
            .transformLatest { requestAuthorizeId ->
                logger.debug {
                    "[AuthCheckLoop][${requestAuthorizeId.idStr}], checking authorize state."
                }
                
                authorizeTasker.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        sessionManager.requireAuthorize(
                            onLaunch = { onLaunchAuthorize(requestAuthorizeId) },
                            skipOnGuest = true,
                        )
                    } catch (_: AuthorizationCancelledException) {
                    } catch (_: AuthorizationException) {
                    } catch (e: Throwable) {
                        throw IllegalStateException("Unknown exception during requireAuth, see cause", e)
                    }
                }

                if (requestAuthorizeId == REFRESH) return@transformLatest emit(null)
                emitAll(
                    sessionManager.processingRequest
                        .filterNotNull()
                        .map { requestAuthorizeId to it },
                )
            }
            .filterNotNull() // filter out refresh
            .collectLatest { (requestAuthorizeId, processingRequest) ->
                logger.debug {
                    "[AuthCheckLoop][${requestAuthorizeId.idStr}] Current processing request: $processingRequest"
                }

                // 最大尝试 300 次, 每次间隔 1 秒
                suspend { checkAuthorizeStatus(requestAuthorizeId, processingRequest) }
                    .asFlow()
                    .retry(retries = NETWORK_MAX_RETRIES) { e ->
                        // 网络问题先重试有限次, 超过次数就没必要继续了
                        (e is ApiNetworkException).also { if (it) delay(awaitRetryInterval) }
                    }
                    .retry { e ->
                        (e is NotAuthorizedException).also { if (it) delay(awaitRetryInterval) }
                    }
                    .catch { e ->
                        if (e is ApiNetworkException) {
                            logger.error { 
                                "[AuthCheckLoop][${requestAuthorizeId.idStr}] Failed to check authorize status " +
                                        "due to network error." 
                            }
                        } else if (e !is NotAuthorizedException) {
                            logger.error { 
                                "[AuthCheckLoop][${requestAuthorizeId.idStr}] Failed to check authorize status." 
                            }
                        }
                        authorizeTasker.cancel(CancellationException(cause = e)) 
                    }
                    .firstOrNull()
            }
    }

    suspend fun startAuthorize() {
        sessionManager.clearSession()
        currentRequestAuthorizeId.value = Uuid.random().toString()
    }

    fun cancelAuthorize() {
        authorizeTasker.cancel()
        currentRequestAuthorizeId.value = null
    }

    fun checkAuthorizeState() {
        currentRequestAuthorizeId.value = REFRESH
    }

    /**
     * 通过 token 授权
     */
    suspend fun setAuthorizationToken(token: String) {
        sessionManager.setSession(
            AccessTokenSession(
                accessToken = token,
                expiresAtMillis = currentTimeMillis() + 365.days.inWholeMilliseconds,
            ),
        )
        // trigger ui update
        currentRequestAuthorizeId.value = REFRESH
    }

    suspend fun setGuestSession() { 
        sessionManager.setSession(GuestSession)
        currentRequestAuthorizeId.value = REFRESH
    }

    /**
     * 只有验证成功了才会正常返回, 否则会抛出异常
     */
    private suspend fun checkAuthorizeStatus(
        requestId: String,
        request: ExternalOAuthRequest,
    ) {
        val token = authClient
            .getResult(requestId)
            .fold(
                onSuccess = { resp -> resp ?: throw NotAuthorizedException() },
                onKnownFailure = { f -> 
                    if (f is ApiFailure.Unauthorized) throw NotAuthorizedException()
                    throw ApiNetworkException()
                }
            )

        val result = OAuthResult(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresIn = token.expiresIn.seconds,
        )

        logger.debug {
            "[AuthCheckLoop][${requestId.idStr}] " +
                    "Check OAuth result success, request is $request, " +
                    "token expires in ${token.expiresIn.seconds}"
        }
        request.onCallback(Result.success(result))
    }

    /**
     * Combine [SessionStatus] and [ExternalOAuthRequest.State] to [AuthStateNew]
     */
    private suspend fun FlowCollector<AuthStateNew>.collectCombinedAuthState(
        requestId: String,
        sessionState: SessionStatus,
        requestState: ExternalOAuthRequest.State?,
    ) {
        logger.debug {
            "[AuthState][${requestId.idStr}] " +
                    "session: ${sessionState::class.simpleName}, " +
                    "request: ${requestState?.let { it::class.simpleName }}"
        }
        when (sessionState) {
            is SessionStatus.Verified -> {
                val userInfo = sessionState.userInfo
                emit(
                    AuthStateNew.Success(
                        username = userInfo.username ?: userInfo.id.toString(),
                        avatarUrl = userInfo.avatarUrl,
                        isGuest = false,
                    ),
                )
            }

            is SessionStatus.Loading -> {
                emit(AuthStateNew.AwaitingResult(requestId))
            }

            SessionStatus.NetworkError,
            SessionStatus.ServiceUnavailable -> {
                emit(AuthStateNew.NetworkError)
            }

            SessionStatus.Expired -> {
                emit(AuthStateNew.TokenExpired)
            }

            SessionStatus.NoToken -> when (requestState) {
                ExternalOAuthRequest.State.Launching,
                ExternalOAuthRequest.State.AwaitingCallback,
                ExternalOAuthRequest.State.Processing -> {
                    emit(AuthStateNew.AwaitingResult(requestId))
                }

                is ExternalOAuthRequest.State.Failed -> {
                    emit(AuthStateNew.UnknownError(requestState.throwable.toString()))
                }

                is ExternalOAuthRequest.State.Cancelled -> {
                    when (val ex = requestState.cause.cause?.cause) {
                        is ApiNetworkException -> emit(AuthStateNew.NetworkError)
                        
                        !is NotAuthorizedException -> emit(AuthStateNew.UnknownError(ex.toString()))
                        
                        else -> emit(AuthStateNew.Idle)
                    }
                }

                else -> {}
            }

            SessionStatus.Guest -> emit(AuthStateNew.Success("", null, isGuest = true))
        }
    }
    
    companion object {
        private const val REFRESH = "-1"
        private const val NETWORK_MAX_RETRIES = 10L
        private val String.idStr get() = if (equals(REFRESH)) "REFRESH" else this
    }
}

// This class is intend to replace current [AuthState]
@Stable
sealed class AuthStateNew {
    @Immutable
    data object Idle : AuthStateNew()

    @Stable
    data class AwaitingResult(val requestId: String) : AuthStateNew()

    sealed class Error : AuthStateNew()

    @Immutable
    data object NetworkError : Error()

    @Immutable
    data object TokenExpired : Error()
    
    @Stable
    data class UnknownError(val message: String) : Error()

    @Stable
    data class Success(
        val username: String,
        val avatarUrl: String?,
        val isGuest: Boolean
    ) : AuthStateNew()
}

/**
 * 还未完成验证, API 返回 null 或 [ApiFailure.Unauthorized]
 */
private class NotAuthorizedException : Exception()

/**
 * 网路问题, [ApiFailure.NetworkError] 和 [ApiFailure.ServiceUnavailable]
 */
private class ApiNetworkException : Exception()
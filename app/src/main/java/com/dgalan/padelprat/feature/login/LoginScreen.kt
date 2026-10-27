package com.dgalan.padelprat.feature.login

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgalan.padelprat.R
import com.dgalan.padelprat.ui.theme.PadelPratTheme
import com.dgalan.padelprat.ui.theme.padelColors

@Composable
fun LoginScreen(modifier: Modifier = Modifier, viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LightStatusBarIcons()
    LoginContent(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onLoginClick = viewModel::onLoginClick,
        modifier = modifier
    )
}

/** La cabecera azul queda detrás de la barra de estado, así que sus iconos van en blanco mientras se ve el login. */
@Composable
private fun LightStatusBarIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

@Composable
private fun LoginContent(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Entrada de la pantalla: la pista y el formulario aparecen con un fundido
    val formAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        formAlpha.animateTo(1f, tween(durationMillis = 500, delayMillis = 150))
    }

    AdaptiveVerticalLayout(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding(),
        top = { LoginHeader() },
        middle = {
            CourtIllustration(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .graphicsLayer { alpha = formAlpha.value }
            )
        },
        bottom = {
            LoginForm(
                uiState = uiState,
                onEmailChange = onEmailChange,
                onPasswordChange = onPasswordChange,
                onTogglePasswordVisibility = onTogglePasswordVisibility,
                onLoginClick = onLoginClick,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
                    .graphicsLayer { alpha = formAlpha.value }
            )
        }
    )
}

private enum class AdaptiveSlot { Top, Middle, Bottom }

/**
 * [top] arriba, [bottom] pegado abajo y [middle] ocupando solo el hueco que queda entre los dos.
 * Si ese hueco es menor que [minMiddleHeight] (móviles pequeños o teclado abierto) el centro ni siquiera se compone,
 * así que sus animaciones se paran, y si ni así cabe todo, la pantalla hace scroll: los elementos nunca se solapan.
 */
@Composable
private fun AdaptiveVerticalLayout(
    top: @Composable () -> Unit,
    middle: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minMiddleHeight: Dp = 120.dp
) {
    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = constraints.maxHeight
        SubcomposeLayout(modifier = Modifier.verticalScroll(rememberScrollState())) { layoutConstraints ->
            val width = layoutConstraints.maxWidth
            val childConstraints = Constraints(maxWidth = width)
            val topPlaceables = subcompose(AdaptiveSlot.Top, top).map { it.measure(childConstraints) }
            val bottomPlaceables = subcompose(AdaptiveSlot.Bottom, bottom).map { it.measure(childConstraints) }
            val topHeight = topPlaceables.sumOf { it.height }
            val bottomHeight = bottomPlaceables.sumOf { it.height }

            val freeHeight = viewportHeight - topHeight - bottomHeight
            val middlePlaceables = if (freeHeight >= minMiddleHeight.roundToPx()) {
                subcompose(AdaptiveSlot.Middle, middle).map { it.measure(Constraints.fixed(width, freeHeight)) }
            } else {
                emptyList()
            }
            val middleHeight = if (middlePlaceables.isEmpty()) 0 else freeHeight
            val height = maxOf(viewportHeight, topHeight + middleHeight + bottomHeight)

            layout(width, height) {
                var y = 0
                topPlaceables.forEach {
                    it.place(0, y)
                    y += it.height
                }
                middlePlaceables.forEach { it.place(0, y) }
                y = height - bottomHeight
                bottomPlaceables.forEach {
                    it.place(0, y)
                    y += it.height
                }
            }
        }
    }
}

@Composable
private fun LoginHeader(modifier: Modifier = Modifier) {
    // En oscuro un bloque de primary sería demasiado claro, así que se usa primaryContainer
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val background = if (dark) scheme.primaryContainer else scheme.primary
    val content = if (dark) scheme.onPrimaryContainer else scheme.onPrimary
    val subtitle = if (dark) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.primaryContainer

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 28.dp)
    ) {
        BouncingBall()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = content
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = subtitle
        )
    }
}

/** El círculo lima con la bola cae desde arriba y bota al abrir la pantalla. */
@Composable
private fun BouncingBall(modifier: Modifier = Modifier) {
    val offsetY = remember { Animatable(-120f) }
    LaunchedEffect(Unit) {
        offsetY.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    val ballBackground = MaterialTheme.padelColors.accent
    val ballIcon = MaterialTheme.padelColors.onAccent

    Box(
        modifier = modifier
            .graphicsLayer { translationY = offsetY.value.dp.toPx() }
            .size(48.dp)
            .background(ballBackground, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ball),
            contentDescription = null,
            tint = ballIcon,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun LoginForm(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldShape = RoundedCornerShape(12.dp)
    val invalidCredentials = uiState.error == LoginError.InvalidCredentials
    // Iconos de email y candado siempre en azul para enlazar con la cabecera y la pista; el ojo, que es un botón,
    // se queda en el color por defecto
    val accent = MaterialTheme.colorScheme.primary
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedLeadingIconColor = accent,
        unfocusedLeadingIconColor = accent,
        errorLeadingIconColor = accent
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.login_email)) },
            placeholder = { Text(stringResource(R.string.login_email_placeholder)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_mail), contentDescription = null) },
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors,
            enabled = !uiState.isLoading,
            isError = invalidCredentials,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            // Para que los gestores de contraseñas lo rellenen
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress + ContentType.Username }
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.login_password)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_lock), contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onTogglePasswordVisibility) {
                    val visible = uiState.isPasswordVisible
                    Icon(
                        painter = painterResource(
                            if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                        ),
                        contentDescription = stringResource(
                            if (visible) R.string.login_hide_password else R.string.login_show_password
                        )
                    )
                }
            },
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors,
            visualTransformation = if (uiState.isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            enabled = !uiState.isLoading,
            isError = invalidCredentials,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLoginClick() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password }
        )
        // Un error del último intento tiene prioridad sobre el aviso de sesión caducada
        val message = uiState.error?.messageRes()
            ?: R.string.login_session_expired.takeIf { uiState.isSessionExpired }
        if (message != null) {
            Text(
                text = stringResource(message),
                color = if (uiState.error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                // TalkBack lo lee en cuanto aparece
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Button(
            onClick = onLoginClick,
            enabled = uiState.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(48.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.login_button), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun LoginError.messageRes(): Int = when (this) {
    LoginError.InvalidCredentials -> R.string.login_error_invalid_credentials
    LoginError.Network -> R.string.login_error_network
    LoginError.Unknown -> R.string.login_error_unknown
}

@Preview
@Composable
private fun LoginContentPreview() {
    PadelPratTheme {
        LoginContent(
            uiState = LoginUiState(email = "jugador@padelprat.com"),
            onEmailChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onLoginClick = {}
        )
    }
}

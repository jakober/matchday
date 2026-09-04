package com.jakober.matchday.ui.auth

import com.jakober.matchday.i18n.S
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jakober.matchday.theme.CardCorner

/**
 * Anmelden oder Registrieren. Ein Bildschirm mit Umschalter statt zwei:
 * Wer hier landet, weiss oft nicht, ob er schon ein Konto hat - der Wechsel
 * soll einen Tipp kosten, nicht eine Navigation.
 *
 * Aufbau wie beim Einstieg: Aussenspalte mit systemBars und ime getrennt,
 * innen scrollbar, der Knopf ausserhalb des Scrollbereichs - so bleibt er
 * ueber der Tastatur sichtbar.
 */
@Composable
fun AuthScreen(
    busy: Boolean,
    error: String?,
    /** Bestaetigung, dass eine Mail zum Zuruecksetzen unterwegs ist. */
    notice: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String, email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val emailOk = email.trim().let { "@" in it && "." in it.substringAfter("@") }
    val canSubmit = !busy && emailOk && password.length >= 8 && (!register || name.trim().length >= 2)

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(48.dp))
                Text(
                    text = S.appName,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (register) {
                        S.authIntroRegister
                    } else {
                        S.authIntroSignIn
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(32.dp))

                if (register) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(S.nameQuestion) },
                        singleLine = true,
                        shape = RoundedCornerShape(CardCorner),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(S.emailLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(CardCorner),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (register) S.passwordLabelNew else S.passwordLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(CardCorner),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) S.hidePassword else S.showPassword,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                notice?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (!register) {
                    TextButton(
                        onClick = { onForgotPassword(email.trim()) },
                        enabled = !busy && emailOk,
                    ) {
                        Text(S.forgotPassword)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            Button(
                onClick = {
                    if (register) onSignUp(name.trim(), email.trim(), password)
                    else onSignIn(email.trim(), password)
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(CardCorner),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (register) S.createAccount else S.signIn,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            TextButton(
                onClick = { register = !register },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(
                    text = if (register) S.haveAccount else S.noAccount,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

package com.kirankotla.connectfour.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.kirankotla.connectfour.auth.AuthState
import com.kirankotla.connectfour.auth.AuthViewModel
import com.kirankotla.connectfour.ui.components.ConnectFourEmblem
import com.kirankotla.connectfour.ui.theme.*

@Composable
fun WelcomeAuthScreen(
    authViewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()

    var isSignUpMode by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                isLoading = true
                authViewModel.handleGoogleIdToken(idToken) { success, err ->
                    isLoading = false
                    if (success) {
                        onAuthSuccess()
                    } else {
                        localError = err
                    }
                }
            } else {
                localError = "Failed to retrieve Google token"
            }
        } catch (e: Exception) {
            localError = "Google sign-in canceled or failed: ${e.message}"
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onAuthSuccess()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Branding Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                ) {
                    ConnectFourEmblem(
                        size = 92.dp,
                        animated = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "CONNECT FOUR",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )

                    Text(
                        text = "Play instantly • Compete with friends",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Main Auth Card
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("welcome_auth_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title / Tab Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0F172A))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    isSignUpMode = false
                                    localError = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isSignUpMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    contentColor = if (!isSignUpMode) Color.White else Color(0xFF94A3B8)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Sign In", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    isSignUpMode = true
                                    localError = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSignUpMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    contentColor = if (isSignUpMode) Color.White else Color(0xFF94A3B8)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Sign Up", fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "Continue to start playing",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )

                        // Primary action: Google sign-in stays the most obvious entry point.
                        Button(
                            onClick = {
                                localError = null
                                try {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestIdToken("756945363546-be8klnmqp5gamq3nlm0vthhftbic879m.apps.googleusercontent.com")
                                        .requestEmail()
                                        .build()
                                    val client = GoogleSignIn.getClient(context, gso)
                                    googleSignInLauncher.launch(client.signInIntent)
                                } catch (e: Exception) {
                                    localError = "Failed to launch Google Sign In: ${e.message}"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("continue_with_google_btn")
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Google",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Continue with Google",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }

                        // Secondary path stays simple and easy to scan.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                            Text("or use email", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                        }

                        // Input fields
                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF94A3B8)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        // Error Banner
                        AnimatedVisibility(visible = localError != null) {
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = localError ?: "",
                                    color = Color(0xFFF87171),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Submit Button
                        Button(
                            onClick = {
                                localError = null
                                if (emailInput.isBlank() || passwordInput.isBlank()) {
                                    localError = "Please enter both email and password"
                                    return@Button
                                }
                                isLoading = true
                                if (isSignUpMode) {
                                    val name = nameInput.ifBlank { emailInput.substringBefore("@") }
                                    authViewModel.signUpWithEmail(name, emailInput, passwordInput) { success, err ->
                                        isLoading = false
                                        if (success) {
                                            onAuthSuccess()
                                        } else {
                                            localError = err
                                        }
                                    }
                                } else {
                                    authViewModel.signInWithEmail(emailInput, passwordInput) { success, err ->
                                        isLoading = false
                                        if (success) {
                                            onAuthSuccess()
                                        } else {
                                            localError = err
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text(if (isSignUpMode) "Create Account" else "Sign In with Email", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Guest Play Option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            isLoading = true
                            localError = null
                            authViewModel.signInAsGuest { success, err ->
                                isLoading = false
                                if (success) {
                                    onAuthSuccess()
                                } else {
                                    localError = err
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF38BDF8)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("play_as_guest_btn")
                    ) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Play as Guest", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Play instantly as a guest and jump into a match",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

package com.kirankotla.connectfour.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
    val currentUserId: String? get() = authRepository.currentUserId

    init {
        viewModelScope.launch {
            authRepository.checkInitialAuth()
        }
    }

    fun signInWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = authRepository.signInWithEmail(email.trim(), pass)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Sign in failed")
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, pass: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = authRepository.signUpWithEmail(email.trim(), pass, name.trim())
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Sign up failed")
            }
        }
    }

    fun handleGoogleIdToken(idToken: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = authRepository.signInWithGoogleCredential(idToken)
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Google sign in failed")
            }
        }
    }

    fun signInAsGuest(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = authRepository.signInAsGuest()
            if (res.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Guest sign-in failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun updateProfile(displayName: String, avatarId: String) {
        viewModelScope.launch {
            authRepository.updateProfile(displayName, avatarId)
        }
    }
}

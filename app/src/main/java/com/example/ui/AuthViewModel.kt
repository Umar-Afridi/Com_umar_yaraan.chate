package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AuthRepository(application.applicationContext)
    val authState: StateFlow<AuthState> = repository.authState

    private val _isPrivacyAccepted = MutableStateFlow(repository.isPrivacyPolicyAccepted())
    val isPrivacyAccepted: StateFlow<Boolean> = _isPrivacyAccepted.asStateFlow()

    private val _showEmailSheet = MutableStateFlow(false)
    val showEmailSheet: StateFlow<Boolean> = _showEmailSheet.asStateFlow()

    private val _showForgotPasswordDialog = MutableStateFlow(false)
    val showForgotPasswordDialog: StateFlow<Boolean> = _showForgotPasswordDialog.asStateFlow()

    private val _resetEmailSentMessage = MutableStateFlow<String?>(null)
    val resetEmailSentMessage: StateFlow<String?> = _resetEmailSentMessage.asStateFlow()

    private val _uiToastMessage = MutableStateFlow<String?>(null)
    val uiToastMessage: StateFlow<String?> = _uiToastMessage.asStateFlow()

    fun setPrivacyAccepted(accepted: Boolean) {
        _isPrivacyAccepted.value = accepted
        repository.setPrivacyPolicyAccepted(accepted)
    }

    fun openEmailSheet() {
        if (!_isPrivacyAccepted.value) {
            _uiToastMessage.value = "Please agree to the Privacy Policy first!"
            return
        }
        _showEmailSheet.value = true
    }

    fun closeEmailSheet() {
        _showEmailSheet.value = false
    }

    fun openForgotPasswordDialog() {
        _showForgotPasswordDialog.value = true
    }

    fun closeForgotPasswordDialog() {
        _showForgotPasswordDialog.value = false
    }

    fun setToastMessage(message: String) {
        _uiToastMessage.value = message
    }

    fun clearToastMessage() {
        _uiToastMessage.value = null
    }

    fun clearResetEmailMessage() {
        _resetEmailSentMessage.value = null
    }

    fun signInWithGoogle(activity: Activity) {
        if (!_isPrivacyAccepted.value) {
            _uiToastMessage.value = "Please agree to the Privacy Policy first!"
            return
        }
        viewModelScope.launch {
            repository.signInWithGoogle(activity)
        }
    }

    fun signInWithEmail(email: String, pass: String) {
        if (!_isPrivacyAccepted.value) {
            _uiToastMessage.value = "Please agree to the Privacy Policy first!"
            return
        }
        if (email.isBlank() || pass.isBlank()) {
            _uiToastMessage.value = "Please enter email and password."
            return
        }
        viewModelScope.launch {
            val result = repository.signInWithEmail(email, pass)
            if (result.isSuccess) {
                _showEmailSheet.value = false
            }
        }
    }

    fun signUpWithEmail(username: String, email: String, pass: String) {
        if (!_isPrivacyAccepted.value) {
            _uiToastMessage.value = "Please agree to the Privacy Policy first!"
            return
        }
        if (username.isBlank()) {
            _uiToastMessage.value = "Please enter your username."
            return
        }
        if (email.isBlank() || pass.isBlank()) {
            _uiToastMessage.value = "Please enter email and password."
            return
        }
        if (pass.length < 6) {
            _uiToastMessage.value = "Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            val result = repository.signUpWithEmail(username, email, pass)
            if (result.isSuccess) {
                _showEmailSheet.value = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank() || !email.contains("@")) {
            _uiToastMessage.value = "Please enter a valid email address."
            return
        }
        viewModelScope.launch {
            val result = repository.sendPasswordReset(email)
            if (result.isSuccess) {
                _showForgotPasswordDialog.value = false
                _resetEmailSentMessage.value = "Reset link sent! Please check your email inbox."
            } else {
                _uiToastMessage.value = result.exceptionOrNull()?.message ?: "Failed to send reset link."
            }
        }
    }

    fun onLogoutFromWeb() {
        repository.signOut()
    }

    fun clearError() {
        repository.clearError()
    }
}

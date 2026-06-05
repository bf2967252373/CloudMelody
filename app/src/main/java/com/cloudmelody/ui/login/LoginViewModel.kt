package com.cloudmelody.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudmelody.api.NeteaseApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading    : Boolean = false,
    val loginSuccess : Boolean = false,
    val errorMsg     : String? = null,
    val nickname     : String  = "",
    val avatar       : String  = ""
)

/**
 * ViewModel for LoginActivity.
 * Delegates to NeteaseApi which seeds cookie os=pc before each request.
 */
class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /**
     * Login with phone number.
     * NeteaseApi internally sets cookie os=pc (PC login identifier).
     */
    fun loginPhone(phone: String, password: String, countryCode: String = "86") {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMsg = null)
            when (val result = NeteaseApi.loginPhone(phone, password, countryCode)) {
                is NeteaseApi.LoginResult.Success -> {
                    _state.value = _state.value.copy(
                        isLoading    = false,
                        loginSuccess = true,
                        nickname     = result.nickname,
                        avatar       = result.avatar
                    )
                }
                is NeteaseApi.LoginResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMsg  = result.message
                    )
                }
            }
        }
    }

    /**
     * Login with email.
     * NeteaseApi internally sets cookie os=pc (PC login identifier).
     */
    fun loginEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMsg = null)
            when (val result = NeteaseApi.loginEmail(email, password)) {
                is NeteaseApi.LoginResult.Success -> {
                    _state.value = _state.value.copy(
                        isLoading    = false,
                        loginSuccess = true,
                        nickname     = result.nickname,
                        avatar       = result.avatar
                    )
                }
                is NeteaseApi.LoginResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMsg  = result.message
                    )
                }
            }
        }
    }
}

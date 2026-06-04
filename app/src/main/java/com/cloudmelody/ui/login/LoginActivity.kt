package com.cloudmelody.ui.login

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cloudmelody.R
import com.cloudmelody.databinding.ActivityLoginBinding
import com.cloudmelody.service.MusicService
import com.cloudmelody.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * BlueArchive-themed login screen.
 *
 * Supports:
 *   - 手机号登录 (phone + password)
 *   - 邮筱登录 (email + password)
 *
 * Login identifier: os = pc  (登录标识为 pc)
 * 参考: listen1 netease.js login()
 *   → cookieSet({name:'os', value:'pc'}) before POST
 *   → /weapi/login/cellphone for phone
 *   → /weapi/login for email
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Transparent status bar – BA full-screen aesthetic
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        setupTabs()
        setupInputs()
        setupButtons()
        observeState()
    }

    // ──── Tab switching (phone / email) ────

    private var isPhoneMode = true

    private fun setupTabs() {
        binding.tabPhone.setOnClickListener  { switchTab(phone = true) }
        binding.tabEmail.setOnClickListener  { switchTab(phone = false) }
        switchTab(phone = true)
    }

    private fun switchTab(phone: Boolean) {
        isPhoneMode = phone
        binding.tabPhone.isSelected = phone
        binding.tabEmail.isSelected = !phone
        binding.layoutPhone.visibility = if (phone) View.VISIBLE else View.GONE
        binding.layoutEmail.visibility = if (phone) View.GONE  else View.VISIBLE
        // Update tab indicator color via alpha
        binding.tabPhone.alpha = if (phone) 1f else 0.45f
        binding.tabEmail.alpha = if (phone) 0.45f else 1f
    }

    // ──── IME actions ────

    private fun setupInputs() {
        // Submit on keyboard "Done" / "Go"
        binding.etPhonePassword.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false
        }
        binding.etEmailPassword.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { doLogin(); true } else false
        }
    }

    // ──── Buttons ────

    private fun setupButtons() {
        binding.btnLogin.setOnClickListener { doLogin() }
        binding.btnSkip.setOnClickListener  { openMain() }
        binding.btnBack.setOnClickListener  { finish() }
    }

    private fun doLogin() {
        val password = if (isPhoneMode)
            binding.etPhonePassword.text.toString()
        else
            binding.etEmailPassword.text.toString()

        if (password.isBlank()) {
            binding.tvError.text = "请输入密码"
            binding.tvError.visibility = View.VISIBLE
            return
        }

        if (isPhoneMode) {
            val phone   = binding.etPhone.text.toString().trim()
            val country = binding.etCountryCode.text.toString().trim().ifBlank { "86" }
            if (phone.isBlank()) {
                binding.tvError.text = "请输入手机号"
                binding.tvError.visibility = View.VISIBLE
                return
            }
            viewModel.loginPhone(phone, password, country)
        } else {
            val email = binding.etEmail.text.toString().trim()
            if (email.isBlank()) {
                binding.tvError.text = "请输入邮筱"
                binding.tvError.visibility = View.VISIBLE
                return
            }
            viewModel.loginEmail(email, password)
        }
    }

    // ──── Observe ────

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Loading spinner
                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnLogin.isEnabled = !state.isLoading

                    // Error
                    if (state.errorMsg != null) {
                        binding.tvError.text = state.errorMsg
                        binding.tvError.visibility = View.VISIBLE
                    } else {
                        binding.tvError.visibility = View.GONE
                    }

                    // Success → go to main
                    if (state.loginSuccess) {
                        openMain()
                    }
                }
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

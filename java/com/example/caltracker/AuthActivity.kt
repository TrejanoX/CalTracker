package com.example.caltracker

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isPasswordVisible = false
    private var isLoginMode = true // Keeps track of whether we are signing in or registering

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        auth = FirebaseAuth.getInstance()

        // Auto-redirect if user is already logged in
        if (auth.currentUser != null) {
            navigateToDashboard()
        }

        // Initialize Views exactly as named in activity_auth.xml
        val tvAuthTitle = findViewById<TextView>(R.id.tvAuthTitle)
        val etAuthUsername = findViewById<EditText>(R.id.etAuthUsername)
        val etAuthEmail = findViewById<EditText>(R.id.etAuthEmail)
        val etAuthPassword = findViewById<EditText>(R.id.etAuthPassword)
        val btnTogglePasswordVisibility = findViewById<ImageButton>(R.id.btnTogglePasswordVisibility)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val btnMainAction = findViewById<Button>(R.id.btnMainAction)
        val tvToggleMode = findViewById<TextView>(R.id.tvToggleMode)

        // PASSWORD VISIBILITY TOGGLE HANDLER
        btnTogglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etAuthPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePasswordVisibility.setImageResource(android.R.drawable.ic_secure) // Native Android lock icon
            } else {
                etAuthPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePasswordVisibility.setImageResource(android.R.drawable.ic_menu_view) // Native Android eye icon
            }
            etAuthPassword.setSelection(etAuthPassword.text.length)
        }

        // TOGGLE MODE HANDLER (Switches between Login and Registration views)
        tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            if (isLoginMode) {
                tvAuthTitle.text = "CalTracker Login"
                btnMainAction.text = "Sign In"
                tvToggleMode.text = "Don't have an account? Register here"
                etAuthUsername.visibility = View.GONE
                tvForgotPassword.visibility = View.VISIBLE
            } else {
                tvAuthTitle.text = "Create Account"
                btnMainAction.text = "Register"
                tvToggleMode.text = "Already have an account? Sign In here"
                etAuthUsername.visibility = View.VISIBLE
                tvForgotPassword.visibility = View.GONE
            }
        }

        // RESPONSIVE FORGOT PASSWORD ACTION
        tvForgotPassword.setOnClickListener {
            val emailString = etAuthEmail.text.toString().trim()

            if (emailString.isEmpty()) {
                etAuthEmail.error = "Please enter your email address first to reset password"
                etAuthEmail.requestFocus()
            } else {
                Toast.makeText(this, "Sending validation link...", Toast.LENGTH_SHORT).show()

                // Firebase Reset Password Request
                auth.sendPasswordResetEmail(emailString)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(
                                this,
                                "Validation link sent! Open Gmail to change your password, then return here to log in.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Error: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }

        // CORE EXECUTION ACTION BUTTON (Handles both Sign In and Registration)
        btnMainAction.setOnClickListener {
            val email = etAuthEmail.text.toString().trim()
            val password = etAuthPassword.text.toString().trim()

            if (email.isEmpty()) {
                etAuthEmail.error = "Email is required"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etAuthPassword.error = "Password is required"
                return@setOnClickListener
            }

            if (isLoginMode) {
                // RUN FIREBASE SIGN IN FLOW
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                            navigateToDashboard()
                        } else {
                            Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                // RUN FIREBASE NEW ACCOUNT REGISTRATION FLOW
                if (password.length < 6) {
                    etAuthPassword.error = "Password must be at least 6 characters"
                    return@setOnClickListener
                }

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Account registered successfully!", Toast.LENGTH_SHORT).show()
                            navigateToDashboard()
                        } else {
                            Toast.makeText(this, "Registration Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
package com.example.fishapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fishapp.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding : ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        binding.regester.setOnClickListener{
            val intent = Intent(this,SignUpActivity::class.java)
            startActivity(intent)
        }

        binding.imgLog.setOnClickListener{
            val email = binding.editTextEmail.text.toString()
            val password = binding.edittextPassword.text.toString()

            if (validateInput(email, password)) {
                loginUser(email, password)
            }
        }

    }

    private fun loginUser(email: String, password: String) {
        firebaseAuth.signInWithEmailAndPassword(email,password)
            .addOnCompleteListener(this){
                if (it.isSuccessful){
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java) // Replace with your main activity
                    startActivity(intent)
                    finish()
                }
                else{
                    Toast.makeText(this, "Login failed: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            Toast.makeText(this, "Email cannot be empty!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.isEmpty()) {
            Toast.makeText(this, "Password cannot be empty!", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters!", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}
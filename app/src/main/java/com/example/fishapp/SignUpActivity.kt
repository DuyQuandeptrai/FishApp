package com.example.fishapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fishapp.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding : ActivitySignUpBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var database : DatabaseReference
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)


        firebaseAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

        binding.imageView2.setOnClickListener{
            val name = binding.editTextName.text.toString()
            val email = binding.editTextEmail.text.toString()
            val password = binding.editPasswordName.text.toString()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields!", Toast.LENGTH_SHORT).show()
            } else if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters!", Toast.LENGTH_SHORT).show()
            } else {
                registerUser(name, email, password)
            }

        }
    }

    private fun registerUser(name: String, email: String, password: String) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) {
                if (it.isSuccessful) {
                    val userId = firebaseAuth.currentUser?.uid ?: ""
                    val user = User(name, email)

                    database.child(userId).setValue(user)
                        .addOnSuccessListener {
                            // Lưu UID vào SharedPreferences
                            val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                            val editor = sharedPref.edit()
                            editor.putString("userId", userId)
                            editor.apply()

                            Toast.makeText(this, "User registered successfully!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, LoginActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to save user info: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Registration failed: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    data class User(val name: String, val email: String)

}
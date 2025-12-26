package com.example.demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<Button>(R.id.gSignInBtn).setOnClickListener {
            signInGoogle()
        }
    }

    private fun signInGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleResults(task)
        } else {
            Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleResults(task: Task<GoogleSignInAccount>) {
        if (task.isSuccessful) {
            val account = task.result
            if (account != null) {
                updateUI(account)
            } else {
                Toast.makeText(this, "Sign-in failed: account null", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Sign-in failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(account: GoogleSignInAccount) {
        val email = account.email
        val name = account.displayName ?: ""
        val idToken = account.idToken

        // ✅ Role detection (student / teacher / HOD)
        var role = ""
        if (email != null) {
            when {
                email.endsWith("@gujaratvidyapith.org") -> role = "student"
                email.equals("varunhirapara5291@gmail.com", ignoreCase = true) -> role = "Clerk"
                email.contains("hod", ignoreCase = true) ||
                        email.endsWith("varunhirapara280@gmail.com") -> role = "hod"

                else -> {
                    Toast.makeText(this, "Access denied: unauthorized email domain", Toast.LENGTH_LONG).show()
                    googleSignInClient.signOut()
                    return
                }
            }
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
            if (authTask.isSuccessful) {
                val user = auth.currentUser
                val userUID = user?.uid
                Log.d("USER_UID", "Logged-in user's UID: $userUID")

                // Save locally
                val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("collegeEmail", email)
                    putString("currentName", name)
                    putString("userUID", userUID)
                    putString("role", role)
                    apply()
                }

                // Save to Firestore
                saveUserToFirestore(userUID, name, email ?: "", role, user?.photoUrl.toString())

                // Move to next screen
                val intent = Intent(this, navMain::class.java)
                intent.putExtra("openProfile", true)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Firebase Auth failed: ${authTask.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ Save user info to Firestore
    private fun saveUserToFirestore(uid: String?, name: String, email: String, role: String, photoUrl: String?) {
        if (uid == null) return

        val userMap = hashMapOf(
            "uid" to uid,
            "fullName" to name,
            "email" to email,
            "photo" to (photoUrl ?: ""),
            "role" to role,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("users").document(uid)
            .set(userMap)
            .addOnSuccessListener {
                Log.d("Firestore", "User ($role) saved successfully ✅")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving user: ${e.message}")
            }
    }
}

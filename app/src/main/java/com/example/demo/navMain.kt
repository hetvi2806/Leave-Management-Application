package com.example.demo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso

class navMain : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.navmain)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        navigationView.setNavigationItemSelectedListener(this)

        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.open_nav, R.string.close_nav
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        showGoogleUserInfo()

        // ✅ Get user role from SharedPreferences (set during login)
        val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val role = sharedPref.getString("role", "student")

        // ✅ Hide/Show menu items based on role
        val menu = navigationView.menu
        when (role) {
            "Clerk" -> {
                // Teacher → Dashboard, Approvals, Profile
                menu.findItem(R.id.nave_dashboard)?.isVisible = true
                menu.findItem(R.id.nave_Approvals)?.isVisible = true
                menu.findItem(R.id.nave_Profile)?.isVisible = true

                // Hide student-only options
                menu.findItem(R.id.nave_RequestLeave)?.isVisible = false
                menu.findItem(R.id.nave_History)?.isVisible = false
            }

            "hod" -> {
                // ✅ HOD → Dashboard, All Approvals, Profile
                menu.findItem(R.id.nave_dashboard)?.isVisible = true
                menu.findItem(R.id.nave_Approvals)?.isVisible = true
                menu.findItem(R.id.nave_Profile)?.isVisible = true

                // Hide student-only
                menu.findItem(R.id.nave_RequestLeave)?.isVisible = false
                menu.findItem(R.id.nave_History)?.isVisible = false
            }

            else -> {
                // Student → Dashboard, Request Leave, History, Profile
                menu.findItem(R.id.nave_dashboard)?.isVisible = true
                menu.findItem(R.id.nave_RequestLeave)?.isVisible = true
                menu.findItem(R.id.nave_History)?.isVisible = true
                menu.findItem(R.id.nave_Profile)?.isVisible = true

                // Hide teacher-only
                menu.findItem(R.id.nave_Approvals)?.isVisible = true
            }
        }

        // ✅ Default fragment on open
        if (savedInstanceState == null) {
            if (intent.getBooleanExtra("openProfile", false)) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ProfileFragment()).commit()
                navigationView.setCheckedItem(R.id.nave_dashboard)
            } else {
                openDashboardByRole() // 👈 Replaced with role-based dashboard
                navigationView.setCheckedItem(R.id.nave_Profile)
            }
        }
    }

    // ✅ Function to open Dashboard based on role
    private fun openDashboardByRole() {
        val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val role = sharedPref.getString("role", "student")

        val fragment = when (role) {
            "Clerk" -> TeacherDashboardFragment()
            "hod" -> HODDashboardFragment() // 👈 HOD mate alag fragment
            else -> DashboardFragment()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // ✅ Function to open Approvals based on role
    private fun openApprovalsByRole() {
        val sharedPref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val role = sharedPref.getString("role", "student")

        val fragment = when (role) {
            "Clerk" -> TeacherApprovalsFragment()
            "hod" -> HODApprovalsFragment() // 👈 HOD mate alag fragment
            else -> ApprovalsFragment()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun showGoogleUserInfo() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            val name = account.displayName
            val email = account.email
            val photo: Uri? = account.photoUrl

            val headerView: View = navigationView.getHeaderView(0)
            val navName = headerView.findViewById<TextView>(R.id.nav_header_name)
            val navEmail = headerView.findViewById<TextView>(R.id.nav_header_email)
            val navImage = headerView.findViewById<ImageView>(R.id.nav_header_image)

            navName.text = name
            navEmail.text = email

            photo?.let {
                Picasso.get().load(it).into(navImage)
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nave_dashboard -> openDashboardByRole()
            R.id.nave_RequestLeave -> replaceFragment(RequestLeaveFragment())
            R.id.nave_Approvals -> openApprovalsByRole()
            R.id.nave_History -> replaceFragment(HistoryFragment())
            R.id.nave_Profile -> replaceFragment(ProfileFragment())
            R.id.nav_logout -> signOut()
        }

        item.isChecked = true
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun replaceFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            Toast.makeText(this, "Logged out!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}

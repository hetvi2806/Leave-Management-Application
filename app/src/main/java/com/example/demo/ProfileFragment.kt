package com.example.demo

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private lateinit var edtFullName: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtEnrollment: EditText
    private lateinit var edtCourse: EditText
    private lateinit var edtDepartment: EditText
    private lateinit var edtContact: EditText
    private lateinit var btnUpdate: MaterialButton

    private var isEditing = false

    // Firebase
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var currentUserEmail: String
    private var userRole: String = "student"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        auth = FirebaseAuth.getInstance()
        initViews(view)

        // get email & role
        val sharedPref = requireActivity().getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        currentUserEmail = getCurrentUserEmail()
        userRole = sharedPref.getString("role", "student") ?: "student"

        loadUserProfile()
        setupClickListeners()

        return view
    }

    private fun initViews(view: View) {
        edtFullName = view.findViewById(R.id.edtFullName)
        edtEmail = view.findViewById(R.id.edtEmail)
        edtEnrollment = view.findViewById(R.id.edtEnrollment)
        edtCourse = view.findViewById(R.id.edtCourse)
        edtDepartment = view.findViewById(R.id.edtDepartment)
        edtContact = view.findViewById(R.id.edtContact)
        btnUpdate = view.findViewById(R.id.btnUpdate)
    }

    private fun getCurrentUserEmail(): String {
        val firebaseUser = auth.currentUser
        if (firebaseUser?.email != null) return firebaseUser.email!!

        val sharedPref = requireActivity().getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        return sharedPref.getString("collegeEmail", "") ?: ""
    }

    private fun setupClickListeners() {
        btnUpdate.setOnClickListener {
            if (!isEditing) {
                enableEditing(true)
                btnUpdate.text = "Save"
            } else {
                showSaveConfirmationDialog()
            }
        }
    }

    private fun loadUserProfile() {
        edtEmail.setText(currentUserEmail)
        edtEmail.isEnabled = false

        firestore.collection("users").document(currentUserEmail)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    edtFullName.setText(document.getString("fullName") ?: "")
                    edtEnrollment.setText(document.getString("enrollment") ?: "")
                    edtCourse.setText(document.getString("course") ?: "")
                    edtDepartment.setText(document.getString("department") ?: "")
                    edtContact.setText(document.getString("contact") ?: "")
                }
                enableEditing(false)
            }
    }

    private fun enableEditing(enable: Boolean) {
        isEditing = enable

        edtEmail.isEnabled = false

        // Teachers/HOD can't edit enrollment/department/course
        if (userRole == "teacher" || userRole == "hod") {
            edtEnrollment.isEnabled = false
            edtDepartment.isEnabled = false
            edtCourse.isEnabled = false
        } else {
            edtEnrollment.isEnabled = enable
            edtCourse.isEnabled = enable
        }

        edtFullName.isEnabled = enable
        edtContact.isEnabled = enable

        val alpha = if (enable) 1f else 0.6f
        edtFullName.alpha = alpha
        edtEnrollment.alpha = alpha
        edtCourse.alpha = alpha
        edtDepartment.alpha = alpha
        edtContact.alpha = alpha

        btnUpdate.text = if (enable) "Save" else "Edit Profile"
    }

    private fun showSaveConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Update Profile")
            .setMessage("Are you sure you want to save changes?")
            .setPositiveButton("Yes") { dialog, _ ->
                saveUserProfileToFirebase()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun saveUserProfileToFirebase() {
        val progress = ProgressDialog(requireContext()).apply {
            setMessage("Updating profile...")
            setCancelable(false)
            show()
        }

        val userMap = hashMapOf(
            "fullName" to edtFullName.text.toString().trim(),
            "email" to currentUserEmail,
            "enrollment" to edtEnrollment.text.toString().trim(),
            "course" to edtCourse.text.toString().trim(),
            "department" to edtDepartment.text.toString().trim(),
            "contact" to edtContact.text.toString().trim()
        )

        firestore.collection("users")
            .document(currentUserEmail)
            .set(userMap)
            .addOnSuccessListener {
                progress.dismiss()
                enableEditing(false)
                showSuccessDialog("Profile updated successfully!")
            }
            .addOnFailureListener { e ->
                progress.dismiss()
                showErrorDialog("Failed to save profile: ${e.message}")
            }
    }

    private fun showSuccessDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Success")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}

package com.example.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HistoryFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var leaveContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        leaveContainer = view.findViewById(R.id.leaveContainer)

        // Fetch user's leave history
        fetchUserLeaveHistory()

        return view
    }

    private fun fetchUserLeaveHistory() {
        leaveContainer.removeAllViews()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userEmail = currentUser.email
        val userId = currentUser.uid

        Log.d("HISTORY_FRAGMENT", "Fetching leave history for: $userEmail")

        // Try multiple approaches to find leave requests
        tryMultipleApproaches(userEmail, userId)
    }

    private fun tryMultipleApproaches(userEmail: String?, userId: String) {
        // Approach 1: Search in leaveRequests collection by email
        firestore.collection("leaveRequests")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { emailSnapshot ->
                if (!emailSnapshot.isEmpty) {
                    Log.d("HISTORY_FRAGMENT", "Found ${emailSnapshot.size()} leaves by email")
                    displayLeaveRequests(emailSnapshot)
                    return@addOnSuccessListener
                }

                // Approach 2: Search in leaveRequests collection by UID
                firestore.collection("leaveRequests")
                    .whereEqualTo("uid", userId)
                    .get()
                    .addOnSuccessListener { uidSnapshot ->
                        if (!uidSnapshot.isEmpty) {
                            Log.d("HISTORY_FRAGMENT", "Found ${uidSnapshot.size()} leaves by UID")
                            displayLeaveRequests(uidSnapshot)
                            return@addOnSuccessListener
                        }

                        // Approach 3: Search in users collection subcollection
                        searchInUserSubcollection(userId, userEmail)
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("HISTORY_FRAGMENT", "Error fetching leaves: ${exception.message}")
                Toast.makeText(requireContext(), "Error loading leave history", Toast.LENGTH_SHORT).show()
            }
    }

    private fun searchInUserSubcollection(userId: String, userEmail: String?) {
        // First find the user document
        firestore.collection("users")
            .whereEqualTo("uid", userId)
            .get()
            .addOnSuccessListener { userSnapshot ->
                if (userSnapshot.isEmpty) {
                    // If no user found by UID, try by email
                    firestore.collection("users")
                        .whereEqualTo("email", userEmail)
                        .get()
                        .addOnSuccessListener { emailUserSnapshot ->
                            if (emailUserSnapshot.isEmpty) {
                                showNoLeavesFound()
                            } else {
                                fetchLeavesFromUserDocument(emailUserSnapshot.documents[0])
                            }
                        }
                } else {
                    fetchLeavesFromUserDocument(userSnapshot.documents[0])
                }
            }
    }

    private fun fetchLeavesFromUserDocument(userDoc: com.google.firebase.firestore.DocumentSnapshot) {
        firestore.collection("users").document(userDoc.id)
            .collection("leaveRequests")
            .get()
            .addOnSuccessListener { leaveSnapshot ->
                if (leaveSnapshot.isEmpty) {
                    showNoLeavesFound()
                } else {
                    displayLeaveRequests(leaveSnapshot)
                }
            }
            .addOnFailureListener {
                showNoLeavesFound()
            }
    }

    private fun displayLeaveRequests(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        for (doc in snapshot) {
            val data = doc.data
            Log.d("HISTORY_FRAGMENT", "Leave document: $data")

            val name = doc.getString("fullName") ?:
            doc.getString("name") ?:
            doc.getString("email") ?: "Unknown"

            val leaveType = doc.getString("leaveType") ?:
            doc.getString("type") ?: "N/A"

            val startDate = doc.getString("startDate") ?:
            doc.getString("fromDate") ?:
            doc.getString("date") ?: "-"

            val endDate = doc.getString("endDate") ?:
            doc.getString("toDate") ?:
            doc.getString("endDate") ?: "-"

            // ✅ FIX: Get both statuses
            val teacherStatus = doc.getString("teacherStatus") ?: "pending"
            val finalStatus = doc.getString("finalStatus") ?: "pending"
            val reason = doc.getString("reason") ?:
            doc.getString("description") ?:
            doc.getString("purpose") ?: ""

            val card = createLeaveCard(name, leaveType, startDate, endDate, teacherStatus, finalStatus, reason)
            leaveContainer.addView(card)
        }

        // If no cards were added after processing all documents
        if (leaveContainer.childCount == 0) {
            showNoLeavesFound()
        }
    }

    private fun showNoLeavesFound() {
        Toast.makeText(requireContext(), "No leave requests found", Toast.LENGTH_SHORT).show()

        // You can also show a text view in the container
        val noDataText = TextView(requireContext()).apply {
            text = "No leave history found"
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = android.view.Gravity.CENTER
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 50, 0, 0)
        noDataText.layoutParams = params

        leaveContainer.addView(noDataText)
    }

    private fun createLeaveCard(
        name: String,
        leaveType: String,
        startDate: String,
        endDate: String,
        teacherStatus: String,
        finalStatus: String,
        reason: String = ""
    ): View {
        val context = requireContext()

        // ✅ FIX: Determine display status and color based on finalStatus and teacherStatus
        val displayStatus = when (finalStatus.lowercase()) {
            "approved" -> "APPROVED"
            "rejected" -> "REJECTED"
            else -> when (teacherStatus.lowercase()) {
                "approved" -> "TEACHER APPROVED"
                "rejected" -> "TEACHER REJECTED"
                else -> "PENDING"
            }
        }

        val card = CardView(context).apply {
            setContentPadding(24, 24, 24, 24)
            radius = 20f
            cardElevation = 8f
            useCompatPadding = true

            // ✅ FIX: Set colors based on actual status
            val color = when {
                finalStatus.lowercase() == "approved" -> Color.parseColor("#4CAF50") // Green for final approved
                finalStatus.lowercase() == "rejected" -> Color.parseColor("#F44336") // Red for rejected
                teacherStatus.lowercase() == "approved" -> Color.parseColor("#2196F3") // Blue for teacher approved
                teacherStatus.lowercase() == "rejected" -> Color.parseColor("#F44336") // Red for teacher rejected
                else -> Color.parseColor("#FF9800") // Orange for pending
            }
            setCardBackgroundColor(color)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8)
        }

        val tvName = TextView(context).apply {
            text = "👤 $name"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvType = TextView(context).apply {
            text = "Leave Type: $leaveType"
            setTextColor(Color.WHITE)
        }

        val tvDates = TextView(context).apply {
            text = "From $startDate to $endDate"
            setTextColor(Color.WHITE)
        }

        val tvStatus = TextView(context).apply {
            text = "Status: $displayStatus"
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // ✅ FIX: Add status details
        val tvStatusDetails = TextView(context).apply {
            text = when {
                finalStatus.lowercase() == "approved" -> "✅ Final Approved by HOD"
                finalStatus.lowercase() == "rejected" -> "❌ Final Rejected by HOD"
                teacherStatus.lowercase() == "approved" -> "👨‍🏫 Approved by Teacher • ⏳ Waiting for HOD"
                teacherStatus.lowercase() == "rejected" -> "❌ Rejected by Teacher"
                else -> "⏳ Waiting for Teacher Approval"
            }
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        // Add reason if available
        if (reason.isNotEmpty()) {
            val tvReason = TextView(context).apply {
                text = "Reason: $reason"
                setTextColor(Color.WHITE)
            }
            layout.addView(tvReason)
        }

        layout.addView(tvName)
        layout.addView(tvType)
        layout.addView(tvDates)
        layout.addView(tvStatus)
        layout.addView(tvStatusDetails)

        card.addView(layout)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 12, 0, 12)
        card.layoutParams = params

        return card
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         */
        @JvmStatic
        fun newInstance() = HistoryFragment()
    }
}
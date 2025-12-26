package com.example.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ApprovalsFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var leaveContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_approvals, container, false)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        leaveContainer = view.findViewById(R.id.leaveContainer)

        fetchApprovedLeavesForCurrentUser()

        return view
    }

    // ✅ Fetch only FINALLY approved leaves for the logged-in user (by HOD)
    private fun fetchApprovedLeavesForCurrentUser() {
        leaveContainer.removeAllViews()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = currentUser.uid
        Log.d("APPROVALS", "Fetching FINALLY approved leaves for UID: $uid")

        firestore.collection("users")
            .document(uid)
            .collection("leaveRequests")
            .whereEqualTo("finalStatus", "approved") // ✅ Only HOD approved leaves
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    showNoApprovedLeaves()
                    Log.d("APPROVALS", "No FINALLY approved leaves found ❌")
                } else {
                    Log.d("APPROVALS", "Found ${snapshot.size()} FINALLY approved leaves ✅")
                    for (leaveDoc in snapshot) {
                        val leaveType = leaveDoc.getString("leaveType") ?: "N/A"
                        val startDate = leaveDoc.getString("startDate") ?:
                        leaveDoc.getString("fromDate") ?: "-"
                        val endDate = leaveDoc.getString("endDate") ?:
                        leaveDoc.getString("toDate") ?: "-"
                        val totalDays = leaveDoc.getString("totalDays") ?: "N/A"
                        val requestDate = leaveDoc.getString("requestDate") ?:
                        leaveDoc.getString("appliedDate") ?:
                        leaveDoc.getString("createdAt") ?: "-"
                        val teacherStatus = leaveDoc.getString("teacherStatus") ?: "pending"
                        val finalStatus = leaveDoc.getString("finalStatus") ?: "pending"

                        val card = createApprovedLeaveCard(
                            leaveType,
                            startDate,
                            endDate,
                            totalDays,
                            requestDate,
                            teacherStatus,
                            finalStatus
                        )
                        leaveContainer.addView(card)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading approved leaves", Toast.LENGTH_SHORT).show()
                Log.e("APPROVALS", "Error: ${e.message}")
            }
    }

    // ✅ Create a card for FINALLY approved leave
    private fun createApprovedLeaveCard(
        leaveType: String,
        startDate: String,
        endDate: String,
        totalDays: String,
        requestDate: String,
        teacherStatus: String,
        finalStatus: String
    ): View {
        val context = requireContext()

        val card = CardView(context).apply {
            radius = 20f
            cardElevation = 8f
            useCompatPadding = true
            setCardBackgroundColor(Color.parseColor("#4CAF50")) // Green for approved
            setContentPadding(24, 24, 24, 24)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8)
        }

        val tvStatus = TextView(context).apply {
            text = "✅ FINALLY APPROVED BY HOD"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvTeacherStatus = TextView(context).apply {
            text = when (teacherStatus.lowercase()) {
                "approved" -> "👨‍🏫 Teacher: Approved"
                "rejected" -> "👨‍🏫 Teacher: Rejected"
                else -> "👨‍🏫 Teacher: Pending"
            }
            textSize = 14f
            setTextColor(Color.WHITE)
        }

        val tvType = TextView(context).apply {
            text = "📋 Type: $leaveType"
            textSize = 14f
            setTextColor(Color.WHITE)
        }

        val tvDates = TextView(context).apply {
            text = "📅 From $startDate to $endDate"
            textSize = 14f
            setTextColor(Color.WHITE)
        }

        val tvTotal = TextView(context).apply {
            text = "⏱ Total Days: $totalDays"
            textSize = 14f
            setTextColor(Color.WHITE)
        }

        val tvRequestDate = TextView(context).apply {
            text = "📅 Requested On: $requestDate"
            textSize = 14f
            setTextColor(Color.WHITE)
        }

        layout.addView(tvStatus)
        layout.addView(tvTeacherStatus)
        layout.addView(tvType)
        layout.addView(tvDates)
        layout.addView(tvTotal)
        layout.addView(tvRequestDate)
        card.addView(layout)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 12, 0, 12)
        card.layoutParams = params

        return card
    }

    // ✅ No approved leaves message
    private fun showNoApprovedLeaves() {
        val noDataText = TextView(requireContext()).apply {
            text = "No Finally Approved Leaves Found 😶\n(Waiting for HOD Approval)"
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 50, 0, 0)
        noDataText.layoutParams = params

        leaveContainer.addView(noDataText)
    }
}
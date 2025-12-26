package com.example.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore

class HODApprovalsFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var leaveContainer: LinearLayout
    private lateinit var noDataLayout: LinearLayout
    private lateinit var tvPendingCount: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPendingStats: TextView
    private lateinit var tvApprovedCount: TextView
    private lateinit var tvRejectedCount: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_hod_approvals, container, false)
        firestore = FirebaseFirestore.getInstance()

        leaveContainer = view.findViewById(R.id.leaveContainer)
        noDataLayout = view.findViewById(R.id.noDataLayout)
        tvPendingCount = view.findViewById(R.id.tvPendingCount)
        tvTotalCount = view.findViewById(R.id.tvTotalCount)
        tvPendingStats = view.findViewById(R.id.tvPendingStats)
        tvApprovedCount = view.findViewById(R.id.tvApprovedCount)
        tvRejectedCount = view.findViewById(R.id.tvRejectedCount)
        progressBar = view.findViewById(R.id.progressBar)

        loadAllLeaves()
        return view
    }

    private fun loadAllLeaves() {
        leaveContainer.removeAllViews()
        showLoadingState(true)

        Log.d("HOD_APPROVALS", "Starting to fetch leave requests...")

        // Direct approach: Query all leaveRequests subcollections
        firestore.collectionGroup("leaveRequests")
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d("HOD_APPROVALS", "Found ${querySnapshot.size()} total leave requests")

                if (querySnapshot.isEmpty) {
                    Log.d("HOD_APPROVALS", "No leave requests found")
                    showNoLeavesFound()
                    showLoadingState(false)
                    return@addOnSuccessListener
                }

                var totalLeaves = 0
                var pendingLeaves = 0
                var approvedLeaves = 0
                var rejectedLeaves = 0

                for (leaveDoc in querySnapshot) {
                    val finalStatus = leaveDoc.getString("finalStatus")?.lowercase() ?: "pending"
                    val status = leaveDoc.getString("status")?.lowercase() ?: "pending"
                    val userId = leaveDoc.getString("uid") ?: ""

                    Log.d("HOD_APPROVALS", "Processing leave: ${leaveDoc.id}, Status: $status, FinalStatus: $finalStatus")

                    // Count all leaves
                    totalLeaves++

                    when (finalStatus) {
                        "approved" -> approvedLeaves++
                        "rejected" -> rejectedLeaves++
                        else -> pendingLeaves++
                    }

                    // Get user details for this leave
                    if (userId.isNotEmpty()) {
                        firestore.collection("users").document(userId).get()
                            .addOnSuccessListener { userDoc ->
                                val studentName = userDoc.getString("fullName") ?: "Unknown"
                                val leaveType = leaveDoc.getString("leaveType") ?: "-"
                                val startDate = leaveDoc.getString("startDate") ?: "-"
                                val requestDate = leaveDoc.getString("requestDate") ?: "-"
                                val totalDays = leaveDoc.getString("totalDays") ?: ""

                                Log.d("HOD_APPROVALS", "Adding card: $studentName - $leaveType - $finalStatus")

                                requireActivity().runOnUiThread {
                                    leaveContainer.addView(
                                        createLeaveCard(
                                            userId,
                                            leaveDoc.id,
                                            studentName,
                                            leaveType,
                                            startDate,
                                            requestDate,
                                            totalDays,
                                            finalStatus
                                        )
                                    )

                                    // Update statistics after adding each card
                                    updateStatistics(totalLeaves, pendingLeaves, approvedLeaves, rejectedLeaves)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("HOD_APPROVALS", "Error fetching user details: ${e.message}")

                                // Create card with basic info even if user fetch fails
                                val studentName = "Unknown Student"
                                val leaveType = leaveDoc.getString("leaveType") ?: "-"
                                val startDate = leaveDoc.getString("startDate") ?: "-"
                                val requestDate = leaveDoc.getString("requestDate") ?: "-"
                                val totalDays = leaveDoc.getString("totalDays") ?: ""

                                requireActivity().runOnUiThread {
                                    leaveContainer.addView(
                                        createLeaveCard(
                                            userId,
                                            leaveDoc.id,
                                            studentName,
                                            leaveType,
                                            startDate,
                                            requestDate,
                                            totalDays,
                                            finalStatus
                                        )
                                    )

                                    updateStatistics(totalLeaves, pendingLeaves, approvedLeaves, rejectedLeaves)
                                }
                            }
                    } else {
                        Log.e("HOD_APPROVALS", "No user ID found for leave document")
                    }
                }

                showLoadingState(false)
            }
            .addOnFailureListener { e ->
                Log.e("HOD_APPROVALS", "Error fetching leave requests: ${e.message}")
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                showLoadingState(false)
            }
    }

    private fun createLeaveCard(
        userId: String,
        leaveId: String,
        studentName: String,
        leaveType: String,
        startDate: String,
        endDate: String,
        totalDays: String,
        finalStatus: String
    ): View {
        val context = requireContext()
        val card = CardView(context).apply {
            radius = 20f
            cardElevation = 8f
            useCompatPadding = true
            setCardBackgroundColor(
                when (finalStatus.lowercase()) {
                    "approved" -> Color.parseColor("#E8F5E8") // Light green
                    "rejected" -> Color.parseColor("#FFEBEE") // Light red
                    else -> Color.WHITE // White for pending
                }
            )
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20)
        }

        // Status badge
        val statusBadge = TextView(context).apply {
            text = when (finalStatus.lowercase()) {
                "approved" -> "✅ APPROVED"
                "rejected" -> "❌ REJECTED"
                else -> "⏳ PENDING"
            }
            textSize = 14f
            setTextColor(
                when (finalStatus.lowercase()) {
                    "approved" -> Color.parseColor("#4CAF50")
                    "rejected" -> Color.parseColor("#F44336")
                    else -> Color.parseColor("#FF9800")
                }
            )
            gravity = Gravity.END
        }

        // Student name
        val tvName = TextView(context).apply {
            text = "👤 Student: $studentName"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.BLACK)
        }

        // Leave type
        val tvType = TextView(context).apply {
            text = "📘 Leave Type: $leaveType"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        // Leave Dates
        val tvDates = TextView(context).apply {
            text = "📅 Leave Dates: $startDate to $endDate"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        // Request Date (NEW)
        val tvRequestDate = TextView(context).apply {
            text = "📋 Request Date: $startDate"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        // Total days
        val tvTotalDays = TextView(context).apply {
            text = "⏰ Duration: $totalDays"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        // Add all views to layout
        layout.addView(statusBadge)
        layout.addView(tvName)
        layout.addView(tvType)
        layout.addView(tvDates)
        layout.addView(tvRequestDate) // NEW: Request date added
        layout.addView(tvTotalDays)

        // Add buttons only for pending leaves
        if (finalStatus.lowercase() == "pending") {
            val btnLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 16, 0, 0)
                }
            }

            val approveBtn = TextView(context).apply {
                text = "✅ Final Approve"
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                gravity = Gravity.CENTER
                setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 8, 0)
                }
                setOnClickListener {
                    updateFinalStatus(userId, leaveId, "approved", studentName)
                }
            }

            val rejectBtn = TextView(context).apply {
                text = "❌ Reject"
                textSize = 14f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#F44336"))
                gravity = Gravity.CENTER
                setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    updateFinalStatus(userId, leaveId, "rejected", studentName)
                }
            }

            btnLayout.addView(approveBtn)
            btnLayout.addView(rejectBtn)
            layout.addView(btnLayout)
        }

        card.addView(layout)

        // Set card margins
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 12, 0, 12)
        card.layoutParams = params

        return card
    }

    private fun updateFinalStatus(userId: String, leaveId: String, status: String, name: String) {
        Log.d("HOD_APPROVALS", "Updating final status: $status for $name")

        // Update the leave request document
        firestore.collection("users")
            .document(userId)
            .collection("leaveRequests")
            .document(leaveId)
            .update("finalStatus", status)
            .addOnSuccessListener {
                Log.d("HOD_APPROVALS", "Successfully updated final status to: $status")
                val msg = if (status == "approved") "✅ Final Approved for $name" else "❌ Rejected for $name"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                loadAllLeaves() // Refresh the list
            }
            .addOnFailureListener { e ->
                Log.e("HOD_APPROVALS", "Error updating final status: ${e.message}")
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateStatistics(total: Int, pending: Int, approved: Int, rejected: Int) {
        requireActivity().runOnUiThread {
            Log.d("HOD_APPROVALS", "Updating stats - Total: $total, Pending: $pending, Approved: $approved, Rejected: $rejected")

            tvTotalCount.text = total.toString()
            tvPendingStats.text = pending.toString()
            tvApprovedCount.text = approved.toString()
            tvRejectedCount.text = rejected.toString()
            tvPendingCount.text = "$pending pending"

            if (total == 0) {
                Log.d("HOD_APPROVALS", "No leaves found - showing empty state")
                noDataLayout.visibility = View.VISIBLE
                leaveContainer.visibility = View.GONE
            } else {
                noDataLayout.visibility = View.GONE
                leaveContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun showNoLeavesFound() {
        Log.d("HOD_APPROVALS", "No leaves found in database")
        updateStatistics(0, 0, 0, 0)
    }

    private fun showLoadingState(isLoading: Boolean) {
        requireActivity().runOnUiThread {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            Log.d("HOD_APPROVALS", "Loading state: $isLoading")
        }
    }
}
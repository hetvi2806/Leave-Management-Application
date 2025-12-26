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
import com.google.firebase.firestore.FirebaseFirestore

class HODDashboardFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var summaryContainer: LinearLayout
    private lateinit var detailContainer: LinearLayout

    private var totalCount = 0
    private var approvedCount = 0
    private var pendingCount = 0
    private var rejectedCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_hod_dashboard, container, false)

        firestore = FirebaseFirestore.getInstance()
        summaryContainer = view.findViewById(R.id.summaryContainer)
        detailContainer = view.findViewById(R.id.detailContainer)

        fetchAllLeavesForHOD()

        return view
    }

    // 🔹 Fetch all leave requests for HOD
    private fun fetchAllLeavesForHOD() {
        Log.d("HOD_DASHBOARD", "Starting to fetch data...")

        firestore.collectionGroup("leaveRequests")
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d("HOD_DASHBOARD", "Found ${querySnapshot.size()} leave requests")

                if (querySnapshot.isEmpty) {
                    Log.d("HOD_DASHBOARD", "No leave requests found")
                    showNoData()
                    return@addOnSuccessListener
                }

                resetCounts()
                detailContainer.removeAllViews()

                for (leaveDoc in querySnapshot) {
                    totalCount++

                    // Use correct field names from your Firestore data
                    val finalStatus = leaveDoc.getString("finalStatus")?.lowercase() ?: "pending"
                    val status = leaveDoc.getString("status")?.lowercase() ?: "pending"
                    val startDate = leaveDoc.getString("startDate") ?: "-"
                    val endDate = leaveDoc.getString("endDate") ?: "-"
                    val totalDays = leaveDoc.getString("totalDays") ?: "0 days"
                    val leaveType = leaveDoc.getString("leaveType") ?: "-"
                    val requestDate = leaveDoc.getString("requestDate") ?: "-"
                    val userId = leaveDoc.getString("uid") ?: ""

                    Log.d("HOD_DASHBOARD", "Processing leave - FinalStatus: $finalStatus, Status: $status")

                    // 🔹 Decide Status based on your Firestore structure
                    val displayStatus = when {
                        finalStatus == "approved" -> "approved"
                        finalStatus == "rejected" -> "rejected"
                        status == "approved" -> "pending" // Teacher approved, waiting for HOD
                        else -> "pending"
                    }

                    // Count records
                    when (displayStatus) {
                        "approved" -> approvedCount++
                        "pending" -> pendingCount++
                        "rejected" -> rejectedCount++
                    }

                    // Fetch student name from user document
                    if (userId.isNotEmpty()) {
                        firestore.collection("users").document(userId).get()
                            .addOnSuccessListener { userDoc ->
                                val studentName = userDoc.getString("fullName") ?: "Unknown"

                                requireActivity().runOnUiThread {
                                    detailContainer.addView(
                                        createLeaveCard(
                                            studentName, startDate, endDate,
                                            totalDays, displayStatus
                                        )
                                    )
                                    updateSummaryUI()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("HOD_DASHBOARD", "Error fetching user: ${e.message}")
                                // Use default name if user fetch fails
                                requireActivity().runOnUiThread {
                                    detailContainer.addView(
                                        createLeaveCard(
                                            "Unknown Student", startDate, endDate,
                                            totalDays, displayStatus
                                        )
                                    )
                                    updateSummaryUI()
                                }
                            }
                    } else {
                        // If no user ID, use default name
                        requireActivity().runOnUiThread {
                            detailContainer.addView(
                                createLeaveCard(
                                    "Unknown Student", startDate, endDate,
                                    totalDays, displayStatus
                                )
                            )
                            updateSummaryUI()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("HOD_DASHBOARD", "Error loading leaves: ${e.message}")
                Toast.makeText(requireContext(), "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                showNoData()
            }
    }

    // Reset counts
    private fun resetCounts() {
        totalCount = 0
        approvedCount = 0
        pendingCount = 0
        rejectedCount = 0
    }

    // Update Summary Cards
    private fun updateSummaryUI() {
        summaryContainer.removeAllViews()
        summaryContainer.addView(createSummaryCard("Total Requests", totalCount, "#673AB7"))
        summaryContainer.addView(createSummaryCard("Approved", approvedCount, "#4CAF50"))
        summaryContainer.addView(createSummaryCard("Pending", pendingCount, "#FFC107"))
        summaryContainer.addView(createSummaryCard("Rejected", rejectedCount, "#F44336"))
    }

    // Summary Card Design
    private fun createSummaryCard(title: String, count: Int, color: String): View {
        val context = requireContext()

        val card = CardView(context).apply {
            radius = 20f
            cardElevation = 10f
            useCompatPadding = true
            setCardBackgroundColor(Color.parseColor(color))
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25)
        }

        val tvTitle = TextView(context).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvCount = TextView(context).apply {
            text = count.toString()
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        layout.addView(tvTitle)
        layout.addView(tvCount)
        card.addView(layout)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 16, 0, 16)
        card.layoutParams = params

        return card
    }

    // Detailed Leave Card (shows totalDays)
    private fun createLeaveCard(
        studentName: String,
        startDate: String,
        endDate: String,
        totalDays: String,
        status: String
    ): View {

        val context = requireContext()

        val card = CardView(context).apply {
            radius = 20f
            cardElevation = 8f
            useCompatPadding = true
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20)
        }

        val tvName = TextView(context).apply {
            text = "👤 $studentName"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvDate = TextView(context).apply {
            text = "From: $startDate → To: $endDate"
            textSize = 16f
        }

        val tvDays = TextView(context).apply {
            text = "Total Days: $totalDays"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2196F3"))
        }

        val tvStatus = TextView(context).apply {
            text = "Status: ${status.replaceFirstChar { it.uppercase() }}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(
                when (status.lowercase()) {
                    "approved" -> Color.parseColor("#4CAF50")
                    "pending" -> Color.parseColor("#FFC107")
                    "rejected" -> Color.parseColor("#F44336")
                    else -> Color.GRAY
                }
            )
        }

        layout.addView(tvName)
        layout.addView(tvDate)
        layout.addView(tvDays)
        layout.addView(tvStatus)
        card.addView(layout)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 10, 0, 10)
        card.layoutParams = params

        return card
    }

    private fun showNoData() {
        val tv = TextView(requireContext()).apply {
            text = "No leave data available."
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        detailContainer.addView(tv)
    }
}
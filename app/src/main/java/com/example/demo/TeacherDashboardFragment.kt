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

class TeacherDashboardFragment : Fragment() {

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
        val view = inflater.inflate(R.layout.fragment_teacher_dashboard, container, false)
        firestore = FirebaseFirestore.getInstance()

        summaryContainer = view.findViewById(R.id.summaryContainer)
        detailContainer = view.findViewById(R.id.detailContainer)

        fetchAllLeavesForSummary()
        return view
    }

    // 🔹 Fetch all leaves
    private fun fetchAllLeavesForSummary() {
        firestore.collection("users")
            .get()
            .addOnSuccessListener { usersSnapshot ->
                if (usersSnapshot.isEmpty) {
                    showNoData()
                    return@addOnSuccessListener
                }

                resetCounts()
                detailContainer.removeAllViews()

                for (userDoc in usersSnapshot) {
                    val role = userDoc.getString("role") ?: ""
                    val studentName = userDoc.getString("fullName") ?: "Unknown"

                    if (role == "student") {
                        val userId = userDoc.id

                        firestore.collection("users")
                            .document(userId)
                            .collection("leaveRequests")
                            .get()
                            .addOnSuccessListener { leaveSnapshot ->

                                for (leaveDoc in leaveSnapshot) {
                                    totalCount++

                                    val status =
                                        leaveDoc.getString("status")?.lowercase() ?: "pending"
                                    val startDate = leaveDoc.getString("startDate") ?: "-"
                                    val endDate = leaveDoc.getString("endDate") ?: "-"

                                    when (status) {
                                        "approved" -> approvedCount++
                                        "pending" -> pendingCount++
                                        "rejected" -> rejectedCount++
                                    }

                                    // ⛔ Reason removed
                                    detailContainer.addView(
                                        createLeaveCard(
                                            studentName,
                                            startDate,
                                            endDate,
                                            status
                                        )
                                    )
                                }

                                updateSummaryUI()
                            }
                            .addOnFailureListener { e ->
                                Log.e("TeacherDashboard", "Error: ${e.message}")
                            }
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error loading data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetCounts() {
        totalCount = 0
        approvedCount = 0
        pendingCount = 0
        rejectedCount = 0
    }

    // 🔹 Update summary UI
    private fun updateSummaryUI() {
        summaryContainer.removeAllViews()
        summaryContainer.addView(createSummaryCard("Total Leaves", totalCount, "#673AB7"))
        summaryContainer.addView(createSummaryCard("Approved", approvedCount, "#4CAF50"))
        summaryContainer.addView(createSummaryCard("Pending", pendingCount, "#FFC107"))
        summaryContainer.addView(createSummaryCard("Rejected", rejectedCount, "#F44336"))
    }

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

    // 🔹 Detailed leave card (Reason removed)
    private fun createLeaveCard(
        studentName: String,
        startDate: String,
        endDate: String,
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
            text = "From: $startDate  →  To: $endDate"
            textSize = 16f
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
            text = "No leave data found."
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        summaryContainer.addView(tv)
    }
}

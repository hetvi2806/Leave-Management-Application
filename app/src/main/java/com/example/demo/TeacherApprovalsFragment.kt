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
import java.text.SimpleDateFormat
import java.util.*

class TeacherApprovalsFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var leaveContainer: LinearLayout
    private lateinit var noDataLayout: LinearLayout
    private lateinit var tvPendingCount: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPendingStats: TextView
    private lateinit var tvApprovedCount: TextView
    private lateinit var tvRejectedCount: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_approvals, container, false)
        firestore = FirebaseFirestore.getInstance()

        leaveContainer = view.findViewById(R.id.leaveContainer)
        noDataLayout = view.findViewById(R.id.noDataLayout)
        tvPendingCount = view.findViewById(R.id.tvPendingCount)
        tvTotalCount = view.findViewById(R.id.tvTotalCount)
        tvPendingStats = view.findViewById(R.id.tvPendingStats)
        tvApprovedCount = view.findViewById(R.id.tvApprovedCount)
        tvRejectedCount = view.findViewById(R.id.tvRejectedCount)

        loadAllLeaves()
        return view
    }

    private fun loadAllLeaves() {
        leaveContainer.removeAllViews()

        firestore.collection("users")
            .get()
            .addOnSuccessListener { usersSnapshot ->
                if (usersSnapshot.isEmpty) {
                    showNoLeavesFound()
                    return@addOnSuccessListener
                }

                var totalLeaves = 0
                var pendingLeaves = 0
                var approvedLeaves = 0
                var rejectedLeaves = 0

                for (userDoc in usersSnapshot) {
                    val role = userDoc.getString("role") ?: ""
                    if (role == "student") {
                        val userId = userDoc.id

                        firestore.collection("users")
                            .document(userId)
                            .collection("leaveRequests")
                            .get()
                            .addOnSuccessListener { leaveSnapshot ->
                                for (leaveDoc in leaveSnapshot) {
                                    val name = userDoc.getString("fullName") ?: "Unknown"
                                    val reason = leaveDoc.getString("reason") ?: "-"
                                    val teacherStatus = leaveDoc.getString("teacherStatus") ?: "pending"
                                    val finalStatus = leaveDoc.getString("finalStatus") ?: "pending" // ✅ FIX: Declare finalStatus here

                                    val fromDate = leaveDoc.getString("fromDate")
                                        ?: leaveDoc.getString("startDate")
                                        ?: leaveDoc.getString("date") ?: "-"

                                    val toDate = leaveDoc.getString("toDate")
                                        ?: leaveDoc.getString("endDate") ?: "-"

                                    val leaveType = leaveDoc.getString("leaveType") ?: "N/A"
                                    val requestDate = leaveDoc.getString("requestDate") ?: "-"

                                    // Calculate total days
                                    val totalDays = calculateTotalDays(fromDate, toDate)

                                    leaveContainer.addView(
                                        createLeaveCard(
                                            userId,
                                            leaveDoc.id,
                                            name,
                                            leaveType,
                                            reason,
                                            fromDate,
                                            toDate,
                                            requestDate,
                                            totalDays,
                                            teacherStatus,
                                            finalStatus // ✅ FIX: Pass finalStatus here
                                        )
                                    )

                                    totalLeaves++
                                    when (finalStatus.lowercase()) {
                                        "pending" -> pendingLeaves++
                                        "approved" -> approvedLeaves++
                                        "rejected" -> rejectedLeaves++
                                    }
                                }

                                updateStatistics(totalLeaves, pendingLeaves, approvedLeaves, rejectedLeaves)
                            }
                    }
                }
            }
    }

    private fun calculateTotalDays(fromDate: String, toDate: String): String {
        return try {
            if (fromDate == "-" || toDate == "-") {
                return "N/A"
            }

            // Try different date formats
            val dateFormats = arrayOf(
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "dd-MM-yyyy",
                "MM-dd-yyyy"
            )

            var startDate: Date? = null
            var endDate: Date? = null

            for (format in dateFormats) {
                try {
                    val dateFormat = SimpleDateFormat(format, Locale.getDefault())
                    if (startDate == null) {
                        startDate = dateFormat.parse(fromDate)
                    }
                    if (endDate == null) {
                        endDate = dateFormat.parse(toDate)
                    }
                    if (startDate != null && endDate != null) break
                } catch (e: Exception) {
                    // Try next format
                }
            }

            if (startDate != null && endDate != null) {
                val difference = endDate.time - startDate.time
                val days = (difference / (1000 * 60 * 60 * 24)) + 1
                "$days days"
            } else {
                "N/A"
            }
        } catch (e: Exception) {
            Log.e("DATE_CALCULATION", "Error calculating days from '$fromDate' to '$toDate': ${e.message}")
            "N/A"
        }
    }

    private fun updateStatistics(total: Int, pending: Int, approved: Int, rejected: Int) {
        requireActivity().runOnUiThread {
            tvTotalCount.text = total.toString()
            tvPendingStats.text = pending.toString()
            tvApprovedCount.text = approved.toString()
            tvRejectedCount.text = rejected.toString()
            tvPendingCount.text = "$pending pending"

            if (total == 0) {
                noDataLayout.visibility = View.VISIBLE
                leaveContainer.visibility = View.GONE
            } else {
                noDataLayout.visibility = View.GONE
                leaveContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun createLeaveCard(
        userId: String,
        leaveId: String,
        name: String,
        leaveType: String,
        reason: String,
        fromDate: String,
        toDate: String,
        requestDate: String,
        totalDays: String,
        teacherStatus: String,
        finalStatus: String // ✅ FIX: Add finalStatus parameter
    ): View {

        val context = requireContext()
        val card = CardView(context).apply {
            radius = 20f
            cardElevation = 8f
            useCompatPadding = true

            val bgColor = when (finalStatus.lowercase()) {
                "approved" -> Color.parseColor("#E8F5E8")
                "rejected" -> Color.parseColor("#FFEBEE")
                else -> Color.WHITE
            }
            setCardBackgroundColor(bgColor)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20)
        }

        // Teacher Status Badge
        val teacherStatusBadge = TextView(context).apply {
            text = when (teacherStatus.lowercase()) {
                "approved" -> "✅ TEACHER APPROVED"
                "rejected" -> "❌ TEACHER REJECTED"
                else -> "⏳ PENDING TEACHER APPROVAL"
            }
            textSize = 14f
            setTextColor(
                when (teacherStatus.lowercase()) {
                    "approved" -> Color.parseColor("#4CAF50")
                    "rejected" -> Color.parseColor("#F44336")
                    else -> Color.parseColor("#FF9800")
                }
            )
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
        }

        // Final Status Badge
        val finalStatusBadge = TextView(context).apply {
            text = when (finalStatus.lowercase()) {
                "approved" -> "✅ FINAL APPROVED BY HOD"
                "rejected" -> "❌ FINAL REJECTED BY HOD"
                else -> "⏳ WAITING FOR HOD APPROVAL"
            }
            textSize = 14f
            setTextColor(
                when (finalStatus.lowercase()) {
                    "approved" -> Color.parseColor("#4CAF50")
                    "rejected" -> Color.parseColor("#F44336")
                    else -> Color.parseColor("#FF9800")
                }
            )
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
        }

        val tvName = TextView(context).apply {
            text = "👤 Student: $name"
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val tvType = TextView(context).apply {
            text = "📘 Leave Type: $leaveType"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        val tvDate = TextView(context).apply {
            text = "📅 Dates: $fromDate to $toDate"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        val tvTotalDays = TextView(context).apply {
            text = "⏱️ Total Days: $totalDays"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        val tvRequestDate = TextView(context).apply {
            text = "🕒 Requested On: $requestDate"
            textSize = 15f
            setTextColor(Color.DKGRAY)
        }

        layout.addView(teacherStatusBadge)
        layout.addView(finalStatusBadge)
        layout.addView(tvName)
        layout.addView(tvType)
        layout.addView(tvDate)
        layout.addView(tvTotalDays)
        layout.addView(tvRequestDate)

        // Show buttons only if teacher hasn't approved/rejected yet
        if (teacherStatus.lowercase() == "pending") {
            val buttonsLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 0)
            }

            val approveButton = TextView(context).apply {
                text = "✅ Teacher Approve"
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { updateTeacherStatus(userId, leaveId, "approved", name) }
            }

            val rejectButton = TextView(context).apply {
                text = "❌ Teacher Reject"
                setBackgroundColor(Color.parseColor("#F44336"))
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { updateTeacherStatus(userId, leaveId, "rejected", name) }
            }

            buttonsLayout.addView(approveButton)
            buttonsLayout.addView(rejectButton)
            layout.addView(buttonsLayout)
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

    private fun updateTeacherStatus(userId: String, leaveId: String, status: String, studentName: String) {
        val updateData = hashMapOf<String, Any>(
            "teacherStatus" to status,
            "finalStatus" to "pending" // HOD ની approval માટે pending રાખો
        )

        firestore.collection("users")
            .document(userId)
            .collection("leaveRequests")
            .document(leaveId)
            .update(updateData)
            .addOnSuccessListener {
                val message = when (status) {
                    "approved" -> "✅ Teacher approved leave for $studentName. Waiting for HOD approval."
                    "rejected" -> "❌ Teacher rejected leave for $studentName"
                    else -> "Status updated for $studentName"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                loadAllLeaves()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showNoLeavesFound() {
        updateStatistics(0, 0, 0, 0)
    }
}
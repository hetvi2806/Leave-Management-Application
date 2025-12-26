package com.example.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var tvTitle: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var tvRole: TextView
    private lateinit var leaveListContainer: LinearLayout
    private lateinit var noLeavesLayout: LinearLayout
    private lateinit var btnRequestLeave: MaterialButton
    private lateinit var tvTotalLeaves: TextView
    private lateinit var tvApprovedLeaves: TextView
    private lateinit var tvPendingLeaves: TextView
    private lateinit var tvViewAll: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Initialize views
        tvTitle = view.findViewById(R.id.tvTitle)
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvRole = view.findViewById(R.id.tvRole)
        leaveListContainer = view.findViewById(R.id.leaveListContainer)
        noLeavesLayout = view.findViewById(R.id.noLeavesLayout)
        btnRequestLeave = view.findViewById(R.id.btnRequestLeave)
        tvTotalLeaves = view.findViewById(R.id.tvTotalLeaves)
        tvApprovedLeaves = view.findViewById(R.id.tvApprovedLeaves)
        tvPendingLeaves = view.findViewById(R.id.tvPendingLeaves)
        tvViewAll = view.findViewById(R.id.tvViewAll)

        // Set greeting based on time
        setGreeting()

        // Button -> Open Leave Request Fragment
        btnRequestLeave.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RequestLeaveFragment())
                .addToBackStack(null)
                .commit()
        }

        // View All click listener
        tvViewAll.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HistoryFragment())
                .addToBackStack(null)
                .commit()
        }

        // Load data for logged-in user only
        loadUserDashboard()

        return view
    }

    private fun setGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val greeting = when (hour) {
            in 5..11 -> "Good Morning!"
            in 12..17 -> "Good Afternoon!"
            in 18..21 -> "Good Evening!"
            else -> "Good Night!"
        }
        tvGreeting.text = greeting
    }

    private fun loadUserDashboard() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Please login first!", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = user.uid
        Log.d("DASHBOARD", "Loading dashboard for UID: $uid")

        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("fullName") ?: "User"
                    val role = doc.getString("role") ?: "Student"

                    tvTitle.text = "Welcome, $name"
                    tvRole.text = "Role: $role"

                    Log.d("DASHBOARD", "✅ User info loaded")
                    loadLeaveStats(uid)
                } else {
                    Toast.makeText(requireContext(), "No user profile found!", Toast.LENGTH_SHORT).show()
                    Log.e("DASHBOARD", "❌ User doc not found for UID: $uid")
                }
            }
            .addOnFailureListener { e ->
                Log.e("DASHBOARD", "Error loading user data: ${e.message}")
                Toast.makeText(requireContext(), "Error loading data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadLeaveStats(uid: String) {
        firestore.collection("users")
            .document(uid)
            .collection("leaveRequests")
            .get()
            .addOnSuccessListener { query ->
                var totalLeaves = 0
                var approvedLeaves = 0
                var pendingLeaves = 0
                var rejectedLeaves = 0

                for (doc in query.documents) {
                    totalLeaves++

                    // ✅ FIX: Use finalStatus instead of status
                    val finalStatus = doc.getString("finalStatus") ?: "pending"
                    val teacherStatus = doc.getString("teacherStatus") ?: "pending"

                    when (finalStatus.lowercase()) {
                        "approved" -> approvedLeaves++
                        "rejected" -> rejectedLeaves++
                        else -> pendingLeaves++ // pending includes both teacher pending and teacher approved but HOD pending
                    }
                }

                // Update stats on UI thread
                requireActivity().runOnUiThread {
                    tvTotalLeaves.text = totalLeaves.toString()
                    tvApprovedLeaves.text = approvedLeaves.toString()
                    tvPendingLeaves.text = pendingLeaves.toString()
                }

                // Load recent leaves
                loadRecentLeaves(uid)
            }
            .addOnFailureListener { e ->
                Log.e("DASHBOARD", "Error loading leave stats: ${e.message}")
            }
    }

    private fun loadRecentLeaves(uid: String) {
        leaveListContainer.removeAllViews()

        firestore.collection("users")
            .document(uid)
            .collection("leaveRequests")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(3)
            .get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) {
                    showNoLeavesState()
                    Log.d("DASHBOARD", "⚪ No leave requests")
                } else {
                    hideNoLeavesState()
                    Log.d("DASHBOARD", "✅ ${query.size()} leave requests found")

                    for (doc in query.documents) {
                        val leaveType = doc.getString("leaveType") ?: "N/A"
                        val fromDate = doc.getString("fromDate") ?: doc.getString("startDate") ?: "-"
                        val toDate = doc.getString("toDate") ?: doc.getString("endDate") ?: "-"

                        // ✅ FIX: Get both statuses
                        val teacherStatus = doc.getString("teacherStatus") ?: "pending"
                        val finalStatus = doc.getString("finalStatus") ?: "pending"
                        val reason = doc.getString("reason") ?: ""

                        leaveListContainer.addView(
                            createModernLeaveCard(leaveType, fromDate, toDate, teacherStatus, finalStatus, reason)
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("DASHBOARD", "Error loading leaves: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load leave requests", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createModernLeaveCard(
        leaveType: String,
        fromDate: String,
        toDate: String,
        teacherStatus: String,
        finalStatus: String,
        reason: String
    ): View {
        val context = requireContext()

        val card = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
            radius = 16f
            cardElevation = 2f
            useCompatPadding = true
            setCardBackgroundColor(Color.WHITE)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
        }

        // Header with status
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val typeText = TextView(context).apply {
            text = leaveType
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // ✅ FIX: Show appropriate status based on finalStatus and teacherStatus
        val displayStatus = when (finalStatus.lowercase()) {
            "approved" -> "APPROVED"
            "rejected" -> "REJECTED"
            else -> when (teacherStatus.lowercase()) {
                "approved" -> "TEACHER APPROVED"
                "rejected" -> "TEACHER REJECTED"
                else -> "PENDING"
            }
        }

        val statusText = TextView(context).apply {
            text = displayStatus
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(12, 6, 12, 6)

            // ✅ FIX: Set colors based on actual status
            when {
                finalStatus.lowercase() == "approved" -> setBackgroundColor(Color.parseColor("#4CAF50"))
                finalStatus.lowercase() == "rejected" -> setBackgroundColor(Color.parseColor("#F44336"))
                teacherStatus.lowercase() == "approved" -> setBackgroundColor(Color.parseColor("#2196F3")) // Blue for teacher approved
                teacherStatus.lowercase() == "rejected" -> setBackgroundColor(Color.parseColor("#F44336"))
                else -> setBackgroundColor(Color.parseColor("#FF9800")) // Orange for pending
            }
        }

        headerLayout.addView(typeText)
        headerLayout.addView(statusText)

        // Dates
        val dateText = TextView(context).apply {
            text = "📅 $fromDate → $toDate"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 0)
            }
        }

        // Status details
        val statusDetailsText = TextView(context).apply {
            text = when {
                finalStatus.lowercase() == "approved" -> "✅ Final Approved by HOD"
                finalStatus.lowercase() == "rejected" -> "❌ Final Rejected by HOD"
                teacherStatus.lowercase() == "approved" -> "👨‍🏫 Approved by Teacher • ⏳ Waiting for HOD"
                teacherStatus.lowercase() == "rejected" -> "❌ Rejected by Teacher"
                else -> "⏳ Waiting for Teacher Approval"
            }
            textSize = 12f
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 4, 0, 0)
            }
        }

        // Reason (if available)
        if (reason.isNotEmpty() && reason != "-") {
            val reasonText = TextView(context).apply {
                text = "📝 $reason"
                textSize = 13f
                setTextColor(Color.GRAY)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 0)
                }
                maxLines = 2
            }
            layout.addView(reasonText)
        }

        layout.addView(headerLayout)
        layout.addView(dateText)
        layout.addView(statusDetailsText)
        card.addView(layout)

        return card
    }

    private fun showNoLeavesState() {
        requireActivity().runOnUiThread {
            noLeavesLayout.visibility = View.VISIBLE
            leaveListContainer.visibility = View.GONE
        }
    }

    private fun hideNoLeavesState() {
        requireActivity().runOnUiThread {
            noLeavesLayout.visibility = View.GONE
            leaveListContainer.visibility = View.VISIBLE
        }
    }
}
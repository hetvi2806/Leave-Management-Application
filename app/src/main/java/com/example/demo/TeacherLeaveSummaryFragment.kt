package com.example.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TeacherLeaveSummaryFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var summaryContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_summary, container, false)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        summaryContainer = view.findViewById(R.id.summaryContainer)

        loadLeaveSummary()

        return view
    }

    private fun loadLeaveSummary() {
        summaryContainer.removeAllViews()

        firestore.collection("users")
            .get()
            .addOnSuccessListener { usersSnapshot ->
                var totalLeaves = 0
                var approvedLeaves = 0
                var pendingLeaves = 0

                if (usersSnapshot.isEmpty) {
                    Toast.makeText(requireContext(), "No users found!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val totalUsers = usersSnapshot.size()
                Log.d("TEACHER", "Found $totalUsers users")

                var processed = 0

                // For each user -> read their leaveRequests subcollection
                for (userDoc in usersSnapshot.documents) {
                    val uid = userDoc.id

                    firestore.collection("users")
                        .document(uid)
                        .collection("leaveRequests")
                        .get()
                        .addOnSuccessListener { leaveSnapshot ->
                            for (leaveDoc in leaveSnapshot) {
                                totalLeaves++
                                when (leaveDoc.getString("status")?.lowercase()) {
                                    "approved" -> approvedLeaves++
                                    "pending" -> pendingLeaves++
                                }
                            }

                            processed++
                            if (processed == totalUsers) {
                                showSummary(totalLeaves, approvedLeaves, pendingLeaves)
                            }
                        }
                        .addOnFailureListener {
                            processed++
                            if (processed == totalUsers) {
                                showSummary(totalLeaves, approvedLeaves, pendingLeaves)
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error loading users", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSummary(total: Int, approved: Int, pending: Int) {
        summaryContainer.removeAllViews()

        val context = requireContext()

        val title = TextView(context).apply {
            text = "📊 Leave Request Summary"
            textSize = 20f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 10, 0, 20)
        }

        summaryContainer.addView(title)

        val totalCard = createSummaryCard("Total Leaves", total.toString(), "#9E9E9E")
        val approvedCard = createSummaryCard("Approved Leaves", approved.toString(), "#4CAF50")
        val pendingCard = createSummaryCard("Pending Leaves", pending.toString(), "#FFC107")

        summaryContainer.addView(totalCard)
        summaryContainer.addView(approvedCard)
        summaryContainer.addView(pendingCard)
    }

    private fun createSummaryCard(label: String, value: String, color: String): View {
        val context = requireContext()

        val card = CardView(context).apply {
            radius = 20f
            cardElevation = 6f
            useCompatPadding = true
            setContentPadding(24, 24, 24, 24)
            setCardBackgroundColor(Color.parseColor(color))
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val labelText = TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val valueText = TextView(context).apply {
            text = value
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        layout.addView(labelText)
        layout.addView(valueText)
        card.addView(layout)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 16, 0, 0)
        card.layoutParams = params

        return card
    }
}

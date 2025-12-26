package com.example.demo

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class RequestLeaveFragment : Fragment() {

    private lateinit var etStartDate: EditText
    private lateinit var etEndDate: EditText
    private lateinit var tvTotalDays: TextView
    private lateinit var spinnerLeaveType: Spinner
    private lateinit var btnSubmit: Button

    // Firebase references
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Leave type limits
    private val SICK_LEAVE_MAX_DAYS = 10
    private val ANNUAL_LEAVE_MAX_DAYS = 30
    private val PERSONAL_LEAVE_MAX_DAYS = 15

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_request_leave, container, false)

        // Initialize UI elements
        etStartDate = view.findViewById(R.id.etStartDate)
        etEndDate = view.findViewById(R.id.etEndDate)
        tvTotalDays = view.findViewById(R.id.tvTotalDays)
        spinnerLeaveType = view.findViewById(R.id.spinnerLeaveType)
        btnSubmit = view.findViewById(R.id.btnSubmit)

        // Spinner values
        val leaveTypes = arrayOf("Annual Leave", "Sick Leave", "Personal Leave")
        spinnerLeaveType.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            leaveTypes
        )

        // Set click listeners
        etStartDate.setOnClickListener { showDatePicker(etStartDate) }
        etEndDate.setOnClickListener { showDatePicker(etEndDate) }
        btnSubmit.setOnClickListener { saveLeaveRequest() }

        return view
    }

    private fun showDatePicker(editText: EditText) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                editText.setText("$day/${month + 1}/$year")
                updateTotalDays()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun updateTotalDays() {
        val start = etStartDate.text.toString()
        val end = etEndDate.text.toString()
        if (start.isNotEmpty() && end.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
                val startDate = sdf.parse(start)
                val endDate = sdf.parse(end)

                if (startDate != null && endDate != null) {
                    val diff = ((endDate.time - startDate.time) / (1000 * 60 * 60 * 24)) + 1
                    tvTotalDays.text = "$diff days"
                    checkLeaveLimit(diff.toInt())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkLeaveLimit(totalDays: Int) {
        val leaveType = spinnerLeaveType.selectedItem?.toString() ?: ""
        val maxDays = getMaxDaysForLeaveType(leaveType)

        if (totalDays > maxDays) {
            tvTotalDays.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            showLimitWarning(leaveType, maxDays)
        } else {
            tvTotalDays.setTextColor(resources.getColor(android.R.color.black))
        }
    }

    private fun getMaxDaysForLeaveType(leaveType: String): Int {
        return when (leaveType) {
            "Sick Leave" -> SICK_LEAVE_MAX_DAYS
            "Annual Leave" -> ANNUAL_LEAVE_MAX_DAYS
            "Personal Leave" -> PERSONAL_LEAVE_MAX_DAYS
            else -> 30
        }
    }

    private fun showLimitWarning(leaveType: String, maxDays: Int) {
        val warningText = "$leaveType cannot exceed $maxDays days!"
        Toast.makeText(requireContext(), warningText, Toast.LENGTH_LONG).show()
    }

    private fun saveLeaveRequest() {
        val startDate = etStartDate.text.toString()
        val endDate = etEndDate.text.toString()
        val leaveType = spinnerLeaveType.selectedItem?.toString() ?: "Unknown"

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = user.uid
        val userEmail = user.email ?: "Unknown"

        if (startDate.isEmpty() || endDate.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val totalDays = calculateTotalDays(startDate, endDate)
        if (totalDays <= 0) {
            Toast.makeText(requireContext(), "End date must be after start date", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateLeaveLimit(leaveType, totalDays)) {
            return
        }

        val calendar = Calendar.getInstance()
        val currentDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(calendar.time)
        val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)

        // ✅ Add HOD & Teacher approval fields
        val leaveData = hashMapOf<String, Any>(
            "uid" to uid,
            "email" to userEmail,
            "leaveType" to leaveType,
            "startDate" to startDate,
            "endDate" to endDate,
            "totalDays" to "$totalDays days",
            "totalDaysCount" to totalDays,
            "teacherApproval" to false,
            "hodApproval" to false,     // ✅ Only HOD can change this
            "status" to "pending",      // ✅ Teacher approval status
            "finalStatus" to "pending", // ✅ Final status depends on HOD
            "requestDateTime" to currentDateTime,
            "requestDate" to currentDate,
            "timestamp" to System.currentTimeMillis()
        )

        saveToFirestore(uid, leaveData)
    }

    private fun calculateTotalDays(startDate: String, endDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("d/M/yyyy", Locale.getDefault())
            val start = sdf.parse(startDate)
            val end = sdf.parse(endDate)
            if (start != null && end != null) {
                ((end.time - start.time) / (1000 * 60 * 60 * 24) + 1).toInt()
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun validateLeaveLimit(leaveType: String, totalDays: Int): Boolean {
        val maxDays = getMaxDaysForLeaveType(leaveType)
        if (totalDays > maxDays) {
            AlertDialog.Builder(requireContext())
                .setTitle("Leave Limit Exceeded")
                .setMessage("$leaveType cannot exceed $maxDays days! You requested $totalDays days.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
            return false
        }
        return true
    }

    private fun saveToFirestore(uid: String, leaveData: Map<String, Any>) {
        firestore.collection("users").document(uid)
            .collection("leaveRequests")
            .add(leaveData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(requireContext(), "Leave request submitted ✅", Toast.LENGTH_SHORT).show()
                clearForm()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun clearForm() {
        etStartDate.text.clear()
        etEndDate.text.clear()
        tvTotalDays.text = ""
        tvTotalDays.setTextColor(resources.getColor(android.R.color.black))
        spinnerLeaveType.setSelection(0)
    }
}+
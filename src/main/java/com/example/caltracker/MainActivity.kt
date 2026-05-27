package com.example.caltracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    // Tracking Metrics State Variables
    private var dailyGoal = 2000
    private var totalConsumed = 0
    private var totalBurned = 0
    private var waterGlassesCount = 0

    // Firebase Engine Architectures
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var userId: String? = null

    // UI View Elements References
    private lateinit var tvCalorieSummary: TextView
    private lateinit var progressBarCalories: ProgressBar
    private lateinit var tvWaterCount: TextView
    private lateinit var llHistoryLogContainer: LinearLayout
    private lateinit var tvEmptyHistoryPlaceholder: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Engines
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        userId = auth.currentUser?.uid

        // Initialize and Bind UI Layout Views
        llHistoryLogContainer = findViewById(R.id.llHistoryLogContainer)
        tvEmptyHistoryPlaceholder = findViewById(R.id.tvEmptyHistoryPlaceholder)
        val tvUserWelcome = findViewById<TextView>(R.id.tvUserWelcome)
        val btnLogOut = findViewById<Button>(R.id.btnLogOut)

        tvCalorieSummary = findViewById(R.id.tvCalorieSummary)
        progressBarCalories = findViewById(R.id.progressBarCalories)

        tvWaterCount = findViewById(R.id.tvWaterCount)
        val btnLogWater = findViewById<Button>(R.id.btnLogWater)

        val etFoodCalories = findViewById<EditText>(R.id.etFoodCalories)
        val btnAddFood = findViewById<Button>(R.id.btnAddFood)

        val btnPresetLight = findViewById<Button>(R.id.btnPresetLight)
        val btnPresetMedium = findViewById<Button>(R.id.btnPresetMedium)
        val btnPresetHeavy = findViewById<Button>(R.id.btnPresetHeavy)

        val etBurnedCalories = findViewById<EditText>(R.id.etBurnedCalories)
        val btnLogExercise = findViewById<Button>(R.id.btnLogExercise)

        val etWeight = findViewById<EditText>(R.id.etWeight)
        val etHeight = findViewById<EditText>(R.id.etHeight)
        val btnCalculateBMI = findViewById<Button>(R.id.btnCalculateBMI)
        val tvBMIResult = findViewById<TextView>(R.id.tvBMIResult)

        // Set User Display Welcome Message
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val usernameDisplay = currentUser.displayName ?: "User"
            tvUserWelcome.text = "Welcome, $usernameDisplay"
        }

        // TO RETRIEVE METRICS ON APP START
        loadUserDataFromCloud()

        // LOGOUT ACTION SESSION HANDLER
        btnLogOut.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Session terminated successfully.", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // WATER HYDRATION EVENT COUNTER
        btnLogWater.setOnClickListener {
            waterGlassesCount++
            tvWaterCount.text = "$waterGlassesCount / 8 Glasses consumed"
            addLogEntry("💧 Hydration: Logged glass #$waterGlassesCount of water")

            saveUserDataToCloud() // Auto-Sync Update to Firestore

            if (waterGlassesCount == 8) {
                Toast.makeText(this, "🎉 Fantastic job! You hit your daily hydration target!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Glass added!", Toast.LENGTH_SHORT).show()
            }
        }

        // FOOD INTAKE MANUAL ADDITION
        btnAddFood.setOnClickListener {
            val calorieString = etFoodCalories.text.toString()
            if (calorieString.isNotEmpty()) {
                val calories = calorieString.toInt()
                totalConsumed += calories
                updateCalorieUI()

                val selectedMealId = findViewById<RadioGroup>(R.id.rgMealCategory).checkedRadioButtonId
                val mealName = findViewById<RadioButton>(selectedMealId).text.toString()
                addLogEntry("🍳 $mealName: Added $calorieString kcal")
                etFoodCalories.text.clear()

                saveUserDataToCloud() // Auto-Sync Update to Firestore
                Toast.makeText(this, "Food element added successfully!", Toast.LENGTH_SHORT).show()
            } else {
                etFoodCalories.error = "Please state a calorie entry amount"
            }
        }

        // PRESET SHORTCUT QUICK-CLICK FAVORITES BUTTONS
        btnPresetLight.setOnClickListener {
            totalConsumed += 100
            updateCalorieUI()
            addLogEntry("☕ Snack Preset: Added 100 kcal")
            saveUserDataToCloud()
            Toast.makeText(this, "Quick added 100 kcal!", Toast.LENGTH_SHORT).show()
        }

        btnPresetMedium.setOnClickListener {
            totalConsumed += 200
            updateCalorieUI()
            addLogEntry("🥪 Light Meal Preset: Added 200 kcal")
            saveUserDataToCloud()
            Toast.makeText(this, "Quick added 200 kcal!", Toast.LENGTH_SHORT).show()
        }

        btnPresetHeavy.setOnClickListener {
            totalConsumed += 300
            updateCalorieUI()
            addLogEntry("🍚 Full Meal Preset: Added 300 kcal")
            saveUserDataToCloud()
            Toast.makeText(this, "Quick added 300 kcal!", Toast.LENGTH_SHORT).show()
        }

        // EXERCISE BURNDOWN TRACKER
        btnLogExercise.setOnClickListener {
            val burnedString = etBurnedCalories.text.toString()
            if (burnedString.isNotEmpty()) {
                val burned = burnedString.toInt()
                totalBurned += burned
                updateCalorieUI()
                addLogEntry("🏃‍♂️ Exercise: Burned $burnedString kcal")
                etBurnedCalories.text.clear()

                saveUserDataToCloud() // Auto-Sync Update to Firestore
                Toast.makeText(this, "Exercise logged successfully!", Toast.LENGTH_SHORT).show()
            } else {
                etBurnedCalories.error = "Please fill in metrics"
            }
        }

        // BMI AND PERSONALIZED CALORIE GOAL COMPUTATION LOGIC
        btnCalculateBMI.setOnClickListener {
            val weightStr = etWeight.text.toString()
            val heightStr = etHeight.text.toString()

            if (weightStr.isNotEmpty() && heightStr.isNotEmpty()) {
                val weight = weightStr.toFloat()
                val height = heightStr.toFloat()

                val heightInMeters = height / 100
                val bmi = weight / heightInMeters.pow(2)

                val classification = when {
                    bmi < 18.5 -> "Underweight"
                    bmi in 18.5..24.9 -> "Normal weight"
                    bmi in 25.0..29.9 -> "Overweight"
                    else -> "Obese"
                }
                tvBMIResult.text = String.format("BMI: %.2f (%s)", bmi, classification)

                // Personalize Daily Goal based on Height and Weight metrics
                val calculatedGoal = ((10 * weight) + (6.25f * height) - 150).toInt()
                dailyGoal = if (calculatedGoal < 1200) 1200 else calculatedGoal

                updateCalorieUI()
                addLogEntry("📊 Health Profile: Calculated new calorie goal of $dailyGoal kcal/day based on metrics ($weightStr kg, $heightStr cm)")

                saveUserDataToCloud() // Auto-Sync Update to Firestore
                Toast.makeText(this, "Daily Calorie Target Personalized!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please fulfill all measurement windows", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // LOCAL DYNAMIC LIST VIEW CONTAINER LOG HANDLER
    private fun addLogEntry(message: String) {
        if (tvEmptyHistoryPlaceholder.visibility == View.VISIBLE) {
            tvEmptyHistoryPlaceholder.visibility = View.GONE
        }

        val newEntryRow = TextView(this)
        newEntryRow.text = message
        newEntryRow.setTextColor(Color.WHITE)
        newEntryRow.textSize = 14f
        newEntryRow.setPadding(0, 8, 0, 8)
        newEntryRow.append("\n-----------------------------------------")
        llHistoryLogContainer.addView(newEntryRow, 0)
    }

    // MAIN DASHBOARD UI SYNC ENGINE
    private fun updateCalorieUI() {
        val netCalories = totalConsumed - totalBurned
        tvCalorieSummary.text = "Net Calories: $netCalories / $dailyGoal kcal"
        progressBarCalories.max = dailyGoal
        progressBarCalories.progress = if (netCalories > 0) netCalories else 0
    }

    // ==========================================
    // TO SAVE/UPDATE METRICS ENGINE DATA LAW
    // ==========================================
    private fun saveUserDataToCloud() {
        val currentUid = userId ?: return

        val trackingPackage = hashMapOf(
            "dailyGoal" to dailyGoal,
            "totalConsumed" to totalConsumed,
            "totalBurned" to totalBurned,
            "waterGlassCount" to waterGlassesCount // Matches your cloud console field naming perfectly
        )

        db.collection("users").document(currentUid)
            .set(trackingPackage, SetOptions.merge())
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Sync Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ==========================================
    // TO RETRIEVE METRICS (ON APP START)
    // ==========================================
    private fun loadUserDataFromCloud() {
        val currentUid = userId ?: return

        db.collection("users").document(currentUid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    dailyGoal = document.getLong("dailyGoal")?.toInt() ?: 2000
                    totalConsumed = document.getLong("totalConsumed")?.toInt() ?: 0
                    totalBurned = document.getLong("totalBurned")?.toInt() ?: 0
                    waterGlassesCount = document.getLong("waterGlassCount")?.toInt() ?: 0

                    // Sync layout elements seamlessly with retrieved values
                    updateCalorieUI()
                    tvWaterCount.text = "$waterGlassesCount / 8 Glasses consumed"
                }
            }
    }
}
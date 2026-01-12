package edu.utem.ftmk.foodallergen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BITP 3453 Mobile Application Development
 * Semester 1 2025/2026
 *
 * @author Emaliana Kasmuri, FTMK, UTeM
 *
 * Purpose:
 * This app predicts food allergens using on-device Small Language Models (SLMs).
 * 
 * Features:
 * - Multi-model support (Llama, Phi, Qwen, Vikhr-Gemma)
 * - Real-time allergen detection from ingredient lists
 * - Performance metrics tracking (Latency, TTFT, ITPS, OTPS, Memory usage)
 * - Firebase Firestore integration for data persistence
 * - Batch prediction with progress tracking
 * - Accuracy calculation comparing predictions with ground truth
 * - Excel data import for testing datasets
 * 
 * Supported Allergens:
 * milk, egg, peanut, tree nut, wheat, soy, fish, shellfish, sesame
 */
class MainActivity : AppCompatActivity() {


    companion object {

        // Load primary native library for JNI functions, core GGML tensor librarym CPU specific
        // implementation and library LLaMa model interaction
        init {
            System.loadLibrary("native-lib")
            System.loadLibrary("ggml-base")
            System.loadLibrary("ggml-cpu")
            //System.loadLibrary("llama/ggml")
            System.loadLibrary("llama")
        }
    }

    // Native function declaration
    external fun inferAllergens(input: String, modelPath: String): String

    private val allowedAllergens = setOf("milk", "egg", "peanut", "tree nut", "wheat", "soy","fish",
        "shellfish", "sesame")

    // Available models
    private val availableModels = listOf(
        "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        "Phi-3-mini-4k-instruct-q4.gguf",
        "Phi-3.5-mini-instruct-Q4_K_M.gguf",
        "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        "qwen2.5-3b-instruct-q4_k_m.gguf",
        "Vikhr-Gemma-2B-instruct-Q4_K_M.gguf"
    )

    private lateinit var recyclerView: RecyclerView
    private lateinit var foodAdapter: FoodAdapter
    private lateinit var spinnerDataSet: Spinner
    private lateinit var spinnerModel: Spinner
    private lateinit var btnLoadDataSet: Button
    private lateinit var btnPredictAll: Button
    private lateinit var btnDashboard: Button
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private var selectedModel: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    
    private val foodList = mutableListOf<FoodData>()
    private var allDataSets: List<List<FoodData>> = emptyList()
    private var currentDataSet: Int = 0
    private var isPredicting = false

    /**
     * Constructs a prompt for the LLM to detect allergens from ingredients
     * 
     * @param ingredients The list of food ingredients as a string
     * @return Formatted prompt with task instructions and allergen rules
     */
    private fun buildPrompt(ingredients: String): String {
        return """Analyze these ingredients and identify allergens.

Ingredients: $ingredients

Allowed allergens: milk, egg, peanut, tree nut, wheat, soy, fish, shellfish, sesame

Output format: List only the allergens found as comma-separated values (e.g., "milk,egg,wheat"). If no allergens are found, output "EMPTY". Do not include explanations or extra text.

Allergens:"""
    }

    /**
     * Main entry point to the application
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // List all available models in filesDir
        listAvailableModels()
        
        copyModelIfNeeded(this)

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView)
        spinnerDataSet = findViewById(R.id.spinnerDataSet)
        spinnerModel = findViewById(R.id.spinnerModel)
        btnLoadDataSet = findViewById(R.id.btnLoadDataSet)
        btnPredictAll = findViewById(R.id.btnPredictAll)
        btnDashboard = findViewById(R.id.btnDashboard)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Setup model spinner
        setupModelSpinner()

        // Set up adapter
        foodAdapter = FoodAdapter(foodList) { food, position ->
            if (!isPredicting) {
                predictSingleItem(food, position)
            }
        }
        recyclerView.adapter = foodAdapter

        // Load data from Excel in background
        loadExcelData()

        // Set up Predict All button
        btnPredictAll.setOnClickListener {
            if (!isPredicting && foodList.isNotEmpty()) {
                predictAllItems()
            }
        }

        // Set up Dashboard button
        btnDashboard.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }

        // Set up Load Data Set button
        btnLoadDataSet.setOnClickListener {
            loadSelectedDataSet()
        }
    }

    private fun loadExcelData() {
        lifecycleScope.launch {
            try {
                tvProgress.text = "Loading Excel data..."
                
                val allFoodData = withContext(Dispatchers.IO) {
                    ExcelReader.readFoodData(this@MainActivity)
                }
                
                if (allFoodData.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "No data found in Excel file",
                        Toast.LENGTH_LONG
                    ).show()
                    tvProgress.text = "No data loaded"
                    return@launch
                }
                
                // Divide into 20 sets
                allDataSets = ExcelReader.divideIntoSets(allFoodData, 20)
                
                // Setup spinner with dataset options
                val dataSetOptions = allDataSets.indices.map { "Data Set ${it + 1} (${allDataSets[it].size} items)" }
                val adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    dataSetOptions
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerDataSet.adapter = adapter
                
                // Load first dataset by default
                loadSelectedDataSet()
                
                Toast.makeText(
                    this@MainActivity,
                    "Loaded ${allFoodData.size} items in ${allDataSets.size} sets",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading Excel data", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error loading data: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                tvProgress.text = "Error loading data"
            }
        }
    }

    private fun loadSelectedDataSet() {
        if (allDataSets.isEmpty()) {
            Toast.makeText(this, "No data sets available", Toast.LENGTH_SHORT).show()
            return
        }
        
        currentDataSet = spinnerDataSet.selectedItemPosition
        foodList.clear()
        foodList.addAll(allDataSets[currentDataSet])
        foodAdapter.notifyDataSetChanged()
        
        tvProgress.text = "Loaded Data Set ${currentDataSet + 1} (${foodList.size} items)"
        Toast.makeText(
            this,
            "Loaded Data Set ${currentDataSet + 1} with ${foodList.size} items",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun predictAllItems() {
        lifecycleScope.launch {
            isPredicting = true
            btnPredictAll.isEnabled = false
            btnLoadDataSet.isEnabled = false
            progressBar.visibility = View.VISIBLE
            progressBar.max = foodList.size
            progressBar.progress = 0
            
            try {
                val totalItems = foodList.size
                var completedItems = 0
                
                for ((index, food) in foodList.withIndex()) {
                    // Update progress
                    tvProgress.text = "Predicting ${index + 1}/$totalItems..."
                    progressBar.progress = index
                    
                    // Perform prediction in background
                    val updatedFood = withContext(Dispatchers.Default) {
                        performPrediction(food)
                    }
                    
                    // Update UI on main thread
                    foodAdapter.updateFood(index, updatedFood)
                    
                    // Save to Firebase
                    val saveResult = FirebaseRepository.saveFoodPrediction(updatedFood)
                    if (saveResult.isSuccess) {
                        Log.i("MainActivity", "Saved ${updatedFood.name} to Firebase")
                    } else {
                        Log.e("MainActivity", "Failed to save ${updatedFood.name}", saveResult.exceptionOrNull())
                    }
                    
                    completedItems++
                }
                
                // Calculate all aggregated metrics
                val qualityMetrics = calculateAggregatedQualityMetrics(foodList)
                val safetyMetrics = calculateAggregatedSafetyMetrics(foodList)
                val avgLatency = foodList.mapNotNull { it.metrics?.latencyMs }.average()
                val avgTTFT = foodList.mapNotNull { it.metrics?.ttft }.average()
                
                // Completion notification with comprehensive metrics
                progressBar.progress = totalItems
                tvProgress.text = "EMR: ${String.format("%.1f", qualityMetrics.exactMatchRatio)}% | " +
                        "F1: ${String.format("%.3f", qualityMetrics.microF1)} | " +
                        "Safety: ${String.format("%.1f", safetyMetrics.abstentionAccuracy)}%"
                
                // Log detailed metrics
                Log.i("MainActivity", "=== PREDICTION QUALITY METRICS (Table 2) ===")
                Log.i("MainActivity", "Exact Match Ratio (EMR): ${String.format("%.2f", qualityMetrics.exactMatchRatio)}%")
                Log.i("MainActivity", "Precision: ${String.format("%.4f", qualityMetrics.avgPrecision)}")
                Log.i("MainActivity", "Recall: ${String.format("%.4f", qualityMetrics.avgRecall)}")
                Log.i("MainActivity", "Micro F1: ${String.format("%.4f", qualityMetrics.microF1)}")
                Log.i("MainActivity", "Macro F1: ${String.format("%.4f", qualityMetrics.macroF1)}")
                Log.i("MainActivity", "Hamming Loss: ${String.format("%.4f", qualityMetrics.avgHammingLoss)}")
                Log.i("MainActivity", "False Negative Rate: ${String.format("%.4f", qualityMetrics.falseNegativeRate)}")
                Log.i("MainActivity", "TP=${ qualityMetrics.totalTP}, FP=${qualityMetrics.totalFP}, FN=${qualityMetrics.totalFN}, TN=${qualityMetrics.totalTN}")
                
                Log.i("MainActivity", "=== SAFETY-ORIENTED METRICS (Table 3) ===")
                Log.i("MainActivity", "Hallucination Rate: ${String.format("%.2f", safetyMetrics.hallucinationRate)}%")
                Log.i("MainActivity", "Over-Prediction Rate: ${String.format("%.2f", safetyMetrics.overPredictionRate)}%")
                Log.i("MainActivity", "Abstention Accuracy: ${String.format("%.2f", safetyMetrics.abstentionAccuracy)}%")
                
                Log.i("MainActivity", "=== ON-DEVICE EFFICIENCY METRICS (Table 4) ===")
                Log.i("MainActivity", "Avg Latency: ${String.format("%.2f", avgLatency)}ms")
                Log.i("MainActivity", "Avg TTFT: ${String.format("%.2f", avgTTFT)}ms")
                
                Toast.makeText(
                    this@MainActivity,
                    "✅ EMR: ${String.format("%.1f", qualityMetrics.exactMatchRatio)}% | " +
                            "Micro F1: ${String.format("%.3f", qualityMetrics.microF1)} | " +
                            "Saved to Firebase",
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during prediction", e)
                Toast.makeText(
                    this@MainActivity,
                    "Prediction failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                tvProgress.text = "Prediction failed"
            } finally {
                isPredicting = false
                btnPredictAll.isEnabled = true
                btnLoadDataSet.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun predictSingleItem(food: FoodData, position: Int) {
        lifecycleScope.launch {
            try {
                tvProgress.text = "Predicting ${food.name}..."
                
                val updatedFood = withContext(Dispatchers.Default) {
                    performPrediction(food)
                }
                
                foodAdapter.updateFood(position, updatedFood)
                
                // Save to Firebase
                val saveResult = FirebaseRepository.saveFoodPrediction(updatedFood)
                if (saveResult.isSuccess) {
                    Toast.makeText(
                        this@MainActivity,
                        "Saved to Firebase",
                        Toast.LENGTH_SHORT
                    ).show()
                    tvProgress.text = "Prediction saved"
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to save to Firebase",
                        Toast.LENGTH_SHORT
                    ).show()
                    tvProgress.text = "Save failed"
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error predicting item", e)
                Toast.makeText(
                    this@MainActivity,
                    "Prediction error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                tvProgress.text = "Prediction error"
            }
        }
    }

    private fun performPrediction(food: FoodData): FoodData {
        val ingredients = food.ingredients

        val prompt = buildPrompt(ingredients)
        
        // Get model path
        val modelPath = File(filesDir, selectedModel).absolutePath
        
        // Check if model file exists
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e("MainActivity", "Model file not found: $modelPath")
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Model file not found: ${selectedModel}\nPlease push the model via ADB",
                    Toast.LENGTH_LONG
                ).show()
            }
            return food.copy(
                predictedAllergens = "ERROR: Model file not found",
                timestamp = System.currentTimeMillis(),
                modelName = selectedModel,
                datasetNumber = currentDataSet + 1
            )
        }
        
        Log.i("MainActivity", "Using model: $modelPath (${modelFile.length() / 1024 / 1024} MB)")

        // ---- BEFORE ----
        val javaBefore = MemoryReader.javaHeapKb()
        val nativeBefore = MemoryReader.nativeHeapKb()
        val pssBefore = MemoryReader.totalPssKb()

        val startNs = System.nanoTime()
        val rawResult = inferAllergens(prompt, modelPath)
        val latencyMs = (System.nanoTime() - startNs) / 1_000_000

        // Expected format: TTFT_MS=<value>;ITPS=<value>|<output>
        // Split metadata and output
        val parts = rawResult.split("|", limit = 2)
        val meta = parts[0]
        val rawOutput = if (parts.size > 1) parts[1] else ""
        
        // Check for error conditions
        if (rawOutput.startsWith("ERROR_")) {
            Log.e("MainActivity", "Model inference error: $rawOutput")
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Inference error: ${rawOutput.replace("ERROR_", "").replace("_", " ")}",
                    Toast.LENGTH_LONG
                ).show()
            }
            return food.copy(
                predictedAllergens = "ERROR: ${rawOutput.replace("ERROR_", "").replace("_", " ")}",
                timestamp = System.currentTimeMillis(),
                modelName = selectedModel,
                datasetNumber = currentDataSet + 1
            )
        }
        
        // Check if output is empty (unexpected)
        if (rawOutput.isBlank()) {
            Log.w("MainActivity", "Model returned empty output. Raw result: $rawResult")
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Model returned empty output. Check logs.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Parse TTFT, ITPS, OTPS
        var ttftMs = -1L
        var itps = -1L
        var otps = -1L
        var oetMs = -1L

        meta.split(";").forEach {
            when {
                it.startsWith("TTFT_MS=") ->
                    ttftMs = it.removePrefix("TTFT_MS=").toLongOrNull() ?: -1L
                it.startsWith("ITPS=") ->
                    itps = it.removePrefix("ITPS=").toLongOrNull() ?: -1L
                it.startsWith("OTPS=") ->
                    otps = it.removePrefix("OTPS=").toLongOrNull() ?: -1L
                it.startsWith("OET_MS=") ->
                    oetMs = it.removePrefix("OET_MS=").toLongOrNull() ?: -1L
            }
        }

        // ---- AFTER ----
        val javaAfter = MemoryReader.javaHeapKb()
        val nativeAfter = MemoryReader.nativeHeapKb()
        val pssAfter = MemoryReader.totalPssKb()

        val metrics = InferenceMetrics(
            latencyMs = latencyMs,
            javaHeapKb = javaAfter - javaBefore,
            nativeHeapKb = nativeAfter - nativeBefore,
            totalPssKb = pssAfter - pssBefore,
            ttft = ttftMs,
            itps = itps,
            otps = otps,
            oet = oetMs
        )

        Log.i(
            "SLM_METRICS",
            "Latency=${metrics.latencyMs}ms | TTFT=${ttftMs}ms | ITPS=${itps} tok/s"
        )

        Log.i(
            "SLM_METRICS",
            "OTPS=${otps} tok/s | OET=${oetMs}ms"
        )

        Log.i(
            "SLM_METRICS",
            "Memory → JavaΔ=${metrics.javaHeapKb}KB | " +
                    "NativeΔ=${metrics.nativeHeapKb}KB | " +
                    "PSSΔ=${metrics.totalPssKb}KB"
        )

        val allergens = rawOutput
            .replace("Ġ", "")
            .lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it in allowedAllergens }

        val predictedAllergensText = if (allergens.isEmpty()) {
            "EMPTY"
        } else {
            allergens.joinToString(", ")
        }
        
        // Calculate Prediction Quality Metrics (Table 2)
        val qualityMetrics = calculatePredictionQualityMetrics(
            food.allergensMapped, 
            predictedAllergensText
        )
        
        // Calculate Safety-Oriented Metrics (Table 3)
        val safetyMetrics = calculateSafetyMetrics(
            food.ingredients,
            food.allergensMapped,
            predictedAllergensText
        )

        // Return updated food data with all metrics
        return food.copy(
            predictedAllergens = predictedAllergensText,
            timestamp = System.currentTimeMillis(),
            metrics = metrics,
            modelName = selectedModel,
            datasetNumber = currentDataSet + 1,
            qualityMetrics = qualityMetrics,
            safetyMetrics = safetyMetrics
        )
    }
    
    private fun setupModelSpinner() {
        // Check which models are actually available
        val availableModelFiles = availableModels.filter { modelName ->
            val modelFile = File(filesDir, modelName)
            val exists = modelFile.exists()
            if (exists) {
                Log.i("MainActivity", "Model available: $modelName (${modelFile.length() / 1024 / 1024} MB)")
            } else {
                Log.w("MainActivity", "Model NOT available: $modelName")
            }
            exists
        }
        
        if (availableModelFiles.isEmpty()) {
            Log.e("MainActivity", "No model files found in ${filesDir.absolutePath}")
            Toast.makeText(
                this,
                "No model files found!\nPlease push models via ADB using:\npush-models.ps1",
                Toast.LENGTH_LONG
            ).show()
            // Still show all models in spinner, but they will fail when selected
        }
        
        val modelAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            availableModels
        )
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModel.adapter = modelAdapter
        
        // Set default to qwen2.5-1.5b if available, otherwise first available model
        val defaultModel = if (availableModelFiles.contains("qwen2.5-1.5b-instruct-q4_k_m.gguf")) {
            "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        } else {
            availableModelFiles.firstOrNull() ?: availableModels.first()
        }
        
        val defaultIndex = availableModels.indexOf(defaultModel)
        if (defaultIndex >= 0) {
            spinnerModel.setSelection(defaultIndex)
        }
        
        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedModel = availableModels[position]
                copyModelIfNeeded(this@MainActivity, selectedModel)
                
                // Check if model exists and update UI
                val modelFile = File(filesDir, selectedModel)
                if (modelFile.exists()) {
                    tvProgress.text = "Model: ${selectedModel.substringBefore("-Q")} ✓"
                } else {
                    tvProgress.text = "Model: ${selectedModel.substringBefore("-Q")} ⚠ (NOT FOUND)"
                    Toast.makeText(
                        this@MainActivity,
                        "Model not found. Please push via ADB:\n${modelFile.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun copyModelIfNeeded(context: Context, modelName: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf") {
        val outFile = File(context.filesDir, modelName)

        if (outFile.exists()) {
            Log.i("MainActivity", "Model $modelName already exists at ${outFile.absolutePath} (${outFile.length() / 1024 / 1024} MB)")
            return
        }

        // Try to copy from assets first
        try {
            Log.i("MainActivity", "Copying model $modelName from assets to internal storage...")
            context.assets.open(modelName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i("MainActivity", "Model $modelName copied successfully (${outFile.length() / 1024 / 1024} MB)")
        } catch (e: Exception) {
            // If not in assets, check if it was pushed via ADB
            Log.w("MainActivity", "Model $modelName not found in assets or internal storage")
            Log.w("MainActivity", "Expected location: ${outFile.absolutePath}")
            Log.w("MainActivity", "To push model via ADB, run: push-models.ps1")
        }
    }
    
    private fun listAvailableModels() {
        Log.i("MainActivity", "=== Checking available models in ${filesDir.absolutePath} ===")
        val modelFiles = filesDir.listFiles()?.filter { it.name.endsWith(".gguf") }
        
        if (modelFiles.isNullOrEmpty()) {
            Log.w("MainActivity", "No .gguf model files found!")
            Log.w("MainActivity", "Please push models using: push-models.ps1")
        } else {
            modelFiles.forEach { file ->
                Log.i("MainActivity", "Found model: ${file.name} (${file.length() / 1024 / 1024} MB)")
            }
        }
        Log.i("MainActivity", "=== End of model check ===")
    }
    
    /**
     * Calculate prediction accuracy by comparing predicted allergens with mapped allergens
     */
    private fun calculateAccuracy(foods: List<FoodData>): Double {
        if (foods.isEmpty()) return 0.0
        
        var correctPredictions = 0
        
        foods.forEach { food ->
            if (food.predictedAllergens.isNotEmpty()) {
                val predictedSet = normalizeAllergenString(food.predictedAllergens)
                val mappedSet = normalizeAllergenString(food.allergensMapped)
                
                if (predictedSet == mappedSet) {
                    correctPredictions++
                }
            }
        }
        
        return (correctPredictions.toDouble() / foods.size) * 100.0
    }
    
    /**
     * Normalize allergen string for comparison
     */
    private fun normalizeAllergenString(allergens: String): Set<String> {
        if (allergens.isBlank() || allergens.equals("EMPTY", ignoreCase = true)) {
            return emptySet()
        }
        
        return allergens
            .lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "empty" }
            .toSet()
    }
    
    /**
     * Calculate per-sample Macro F1: Average of per-allergen F1 scores
     * For each of the 9 allergens, calculate binary F1 and average them
     */
    private fun calculatePerSampleMacroF1(actualSet: Set<String>, predictedSet: Set<String>): Double {
        val f1Scores = allowedAllergens.map { allergen ->
            val inActual = allergen in actualSet
            val inPred = allergen in predictedSet
            
            // Binary confusion matrix for this allergen on this sample
            val tp = if (inActual && inPred) 1 else 0
            val fp = if (!inActual && inPred) 1 else 0
            val fn = if (inActual && !inPred) 1 else 0
            
            // F1 for this allergen: 2TP / (2TP + FP + FN)
            if (2 * tp + fp + fn > 0) {
                (2.0 * tp) / (2.0 * tp + fp + fn)
            } else {
                0.0  // No positive cases for this allergen
            }
        }
        return f1Scores.average()
    }
    
    /**
     * Calculate Prediction Quality Metrics (Table 2)
     * Computes TP, FP, FN, TN, Precision, Recall, F1, EMR, Hamming Loss, FNR
     */
    private fun calculatePredictionQualityMetrics(
        groundTruth: String,
        predicted: String
    ): PredictionQualityMetrics {
        val actualSet = normalizeAllergenString(groundTruth)
        val predictedSet = normalizeAllergenString(predicted)
        
        // Confusion matrix components (per-allergen)
        val tp = actualSet.intersect(predictedSet).size  // Correctly identified allergens
        val fp = predictedSet.subtract(actualSet).size   // Incorrectly predicted allergens
        val fn = actualSet.subtract(predictedSet).size   // Missed allergens (DANGEROUS!)
        val tn = allowedAllergens.size - actualSet.size - fp  // Correctly identified as absent
        
        // Precision: TP / (TP + FP)
        val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        
        // Recall: TP / (TP + FN) - CRITICAL for safety
        val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        
        // Micro F1: 2TP / (2TP + FP + FN)
        val microF1 = if (2 * tp + fp + fn > 0) {
            (2.0 * tp) / (2.0 * tp + fp + fn)
        } else 0.0
        
        // Macro F1: Average of per-allergen F1 scores
        // For single-sample, calculate F1 for each of the 9 allergens
        val macroF1 = calculatePerSampleMacroF1(actualSet, predictedSet)
        
        // Exact Match Ratio (per sample)
        val isExactMatch = actualSet == predictedSet
        
        // Hamming Loss: (FP + FN) / (N × L), where N=1 sample, L=9 allergens
        val hammingLoss = (fp + fn).toDouble() / allowedAllergens.size
        
        // False Negative Rate: FN / (TP + FN)
        val fnr = if (tp + fn > 0) fn.toDouble() / (tp + fn) else 0.0
        
        return PredictionQualityMetrics(
            truePositives = tp,
            falsePositives = fp,
            falseNegatives = fn,
            trueNegatives = tn,
            precision = precision,
            recall = recall,
            f1ScoreMicro = microF1,
            f1ScoreMacro = macroF1,
            isExactMatch = isExactMatch,
            hammingLoss = hammingLoss,
            falseNegativeRate = fnr
        )
    }
    
    // Allergen keyword mapping for hallucination detection
    private val allergenKeywords = mapOf(
        "milk" to listOf("milk", "cream", "butter", "cheese", "whey", "casein", "lactose", "dairy", "yogurt", "ghee", "curd", "buttermilk"),
        "egg" to listOf("egg", "albumin", "mayonnaise", "meringue", "ovum", "lysozyme", "ovalbumin"),
        "peanut" to listOf("peanut", "groundnut", "arachis", "monkey nut"),
        "tree nut" to listOf("almond", "walnut", "cashew", "pecan", "pistachio", "hazelnut", "macadamia", "brazil nut", "chestnut", "nut", "praline", "marzipan", "nougat"),
        "wheat" to listOf("wheat", "flour", "gluten", "semolina", "durum", "spelt", "bulgur", "couscous", "bread", "pasta", "noodle", "cereal", "bran", "starch"),
        "soy" to listOf("soy", "soya", "tofu", "edamame", "miso", "tempeh", "lecithin"),
        "fish" to listOf("fish", "anchovy", "sardine", "tuna", "salmon", "cod", "bass", "mackerel", "tilapia", "trout", "herring", "haddock"),
        "shellfish" to listOf("shrimp", "prawn", "crab", "lobster", "crayfish", "oyster", "mussel", "clam", "scallop", "crustacean", "mollusk", "squid", "octopus"),
        "sesame" to listOf("sesame", "tahini", "halvah", "hummus")
    )
    
    /**
     * Check if allergen keywords exist in ingredients
     */
    private fun isAllergenInIngredients(allergen: String, ingredients: String): Boolean {
        val keywords = allergenKeywords[allergen.lowercase()] ?: return false
        val ingredientsLower = ingredients.lowercase()
        return keywords.any { ingredientsLower.contains(it) }
    }
    
    /**
     * Calculate Safety-Oriented Metrics (Table 3)
     * Detects hallucinations, over-predictions, and abstention accuracy
     */
    private fun calculateSafetyMetrics(
        ingredients: String,
        groundTruth: String,
        predicted: String
    ): SafetyMetrics {
        val actualSet = normalizeAllergenString(groundTruth)
        val predictedSet = normalizeAllergenString(predicted)
        
        // Check for hallucinations: predicted allergen not derivable from ingredients
        // An allergen is a hallucination if it's predicted but not in ground truth
        // AND there are no ingredient keywords that could justify the prediction
        val hasHallucination = predictedSet.any { allergen ->
            allergen !in actualSet && !isAllergenInIngredients(allergen, ingredients)
        }
        val hallucinatedAllergens = predictedSet.filter { allergen ->
            allergen !in actualSet && !isAllergenInIngredients(allergen, ingredients)
        }.toList()
        
        // Over-prediction: Predicted allergens beyond ground truth
        val overPredicted = predictedSet.subtract(actualSet).toList()
        val hasOverPrediction = overPredicted.isNotEmpty()
        
        // Missed allergens (for safety analysis)
        val missedAllergens = actualSet.subtract(predictedSet).toList()
        
        // Abstention accuracy: Correctly predicting empty set
        val isCorrectAbstention = if (actualSet.isEmpty()) {
            predictedSet.isEmpty()  // True if correctly predicted no allergens
        } else null  // Not applicable if allergens are present
        
        return SafetyMetrics(
            hasHallucination = hasHallucination,
            hallucinatedAllergens = hallucinatedAllergens,
            hasOverPrediction = hasOverPrediction,
            overPredictedAllergens = overPredicted,
            isCorrectAbstention = isCorrectAbstention,
            missedAllergens = missedAllergens
        )
    }
    
    /**
     * Calculate aggregated quality metrics across all samples
     */
    private fun calculateAggregatedQualityMetrics(foods: List<FoodData>): AggregatedQualityMetrics {
        if (foods.isEmpty()) return AggregatedQualityMetrics()
        
        var totalTP = 0
        var totalFP = 0
        var totalFN = 0
        var totalTN = 0
        var exactMatches = 0
        var sumPrecision = 0.0
        var sumRecall = 0.0
        var sumMacroF1 = 0.0
        var sumHammingLoss = 0.0
        
        foods.forEach { food ->
            food.qualityMetrics?.let { qm ->
                totalTP += qm.truePositives
                totalFP += qm.falsePositives
                totalFN += qm.falseNegatives
                totalTN += qm.trueNegatives
                if (qm.isExactMatch) exactMatches++
                sumPrecision += qm.precision
                sumRecall += qm.recall
                sumMacroF1 += qm.f1ScoreMacro
                sumHammingLoss += qm.hammingLoss
            }
        }
        
        val n = foods.size
        val avgPrecision = sumPrecision / n
        val avgRecall = sumRecall / n
        
        // Micro F1: Aggregate all TP, FP, FN first, then compute F1
        val microF1 = if (2 * totalTP + totalFP + totalFN > 0) {
            (2.0 * totalTP) / (2.0 * totalTP + totalFP + totalFN)
        } else 0.0
        
        // Macro F1: Average of per-sample F1-Macro scores
        val macroF1 = sumMacroF1 / n
        
        // Exact Match Ratio (EMR): Percentage of exact matches
        val emr = (exactMatches.toDouble() / n) * 100.0
        
        // Average Hamming Loss
        val avgHammingLoss = sumHammingLoss / n
        
        // False Negative Rate
        val fnr = if (totalTP + totalFN > 0) {
            totalFN.toDouble() / (totalTP + totalFN)
        } else 0.0
        
        return AggregatedQualityMetrics(
            totalTP = totalTP,
            totalFP = totalFP,
            totalFN = totalFN,
            totalTN = totalTN,
            avgPrecision = avgPrecision,
            avgRecall = avgRecall,
            microF1 = microF1,
            macroF1 = macroF1,
            exactMatchRatio = emr,
            avgHammingLoss = avgHammingLoss,
            falseNegativeRate = fnr,
            totalSamples = n,
            exactMatches = exactMatches
        )
    }
    
    /**
     * Calculate aggregated safety metrics across all samples
     */
    private fun calculateAggregatedSafetyMetrics(foods: List<FoodData>): AggregatedSafetyMetrics {
        if (foods.isEmpty()) return AggregatedSafetyMetrics()
        
        var hallucinationCount = 0
        var overPredictionCount = 0
        var correctAbstentionCount = 0
        var abstentionCases = 0
        
        foods.forEach { food ->
            food.safetyMetrics?.let { sm ->
                if (sm.hasHallucination) hallucinationCount++
                if (sm.hasOverPrediction) overPredictionCount++
                
                sm.isCorrectAbstention?.let { correct ->
                    abstentionCases++
                    if (correct) correctAbstentionCount++
                }
            }
        }
        
        val n = foods.size
        val hallucinationRate = (hallucinationCount.toDouble() / n) * 100.0
        val overPredictionRate = (overPredictionCount.toDouble() / n) * 100.0
        val abstentionAccuracy = if (abstentionCases > 0) {
            (correctAbstentionCount.toDouble() / abstentionCases) * 100.0
        } else 0.0
        
        return AggregatedSafetyMetrics(
            hallucinationRate = hallucinationRate,
            overPredictionRate = overPredictionRate,
            abstentionAccuracy = abstentionAccuracy,
            totalSamples = n,
            hallucinationCount = hallucinationCount,
            overPredictionCount = overPredictionCount,
            correctAbstentionCount = correctAbstentionCount,
            abstentionCases = abstentionCases
        )
    }
}
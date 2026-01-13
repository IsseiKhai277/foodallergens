package edu.utem.ftmk.foodallergen

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
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
 * Fragment for food allergen prediction using on-device SLMs.
 */
class PredictionFragment : Fragment() {

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    // Native function declaration
    private external fun inferAllergens(input: String, modelPath: String): String

    private val allowedAllergens = setOf("milk", "egg", "peanut", "tree nut", "wheat", "soy", "fish",
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
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private var selectedModel: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf"

    private val foodList = mutableListOf<FoodData>()
    private var allDataSets: List<List<FoodData>> = emptyList()
    private var currentDataSet: Int = 0
    private var isPredicting = false
    
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_prediction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // List all available models in filesDir
        listAvailableModels()
        copyModelIfNeeded(requireContext())

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerView)
        spinnerDataSet = view.findViewById(R.id.spinnerDataSet)
        spinnerModel = view.findViewById(R.id.spinnerModel)
        btnLoadDataSet = view.findViewById(R.id.btnLoadDataSet)
        btnPredictAll = view.findViewById(R.id.btnPredictAll)
        tvProgress = view.findViewById(R.id.tvProgress)
        progressBar = view.findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

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

        // Set up Load Data Set button
        btnLoadDataSet.setOnClickListener {
            loadSelectedDataSet()
        }
    }

    private fun buildPrompt(ingredients: String): String {
        return """

            Analyze these ingredients and identify allergens.

            Ingredients: $ingredients

            Allowed allergens: milk, egg, peanut, tree nut, wheat, soy, fish, shellfish, sesame

            Output format: List only the allergens found as comma-separated values (e.g., "milk,egg,wheat"). If no allergens are found, output "EMPTY". Do not include explanations or extra text.

            Allergens:"""
    }

    /*
    promp temlate is different for each models
     */
    private fun wrapWithChatTemplate(promptContent: String): String {
        return when {
            selectedModel.contains("Llama", ignoreCase = true) -> {
                "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\n" +
                promptContent +
                "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
            }
            selectedModel.contains("Phi", ignoreCase = true) -> {
                "<|user|>\n" +
                promptContent +
                "<|end|>\n<|assistant|>\n"
            }
            selectedModel.contains("qwen", ignoreCase = true) -> {
                "<|im_start|>user\n" +
                promptContent +
                "<|im_end|>\n<|im_start|>assistant\n"
            }
            selectedModel.contains("Vikhr", ignoreCase = true) || 
            selectedModel.contains("Gemma", ignoreCase = true) -> {
                "<start_of_turn>user\n" +
                promptContent +
                "<end_of_turn>\n<start_of_turn>model\n"
            }
            else -> promptContent // No wrapping for unknown models
        }
    }

    private fun loadExcelData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Check if fragment is still attached before accessing views
                if (!isAdded || context == null) return@launch
                
                tvProgress.text = "Loading Excel data..."

                val ctx = context ?: return@launch
                val allFoodData = withContext(Dispatchers.IO) {
                    ExcelReader.readFoodData(ctx)
                }

                // Check again after background work
                if (!isAdded || context == null) return@launch

                if (allFoodData.isEmpty()) {
                    context?.let {
                        Toast.makeText(
                            it,
                            "No data found in Excel file",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    tvProgress.text = "No data loaded"
                    return@launch
                }

                // Divide into 20 sets
                allDataSets = ExcelReader.divideIntoSets(allFoodData, 20)

                // Setup spinner with dataset options
                val dataSetOptions = allDataSets.indices.map { "Data Set ${it + 1} (${allDataSets[it].size} items)" }
                context?.let { ctxInner ->
                    val adapter = ArrayAdapter(
                        ctxInner,
                        android.R.layout.simple_spinner_item,
                        dataSetOptions
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerDataSet.adapter = adapter
                }

                // Load first dataset by default
                loadSelectedDataSet()

                context?.let {
                    Toast.makeText(
                        it,
                        "Loaded ${allFoodData.size} items in ${allDataSets.size} sets",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (ex: Exception) {
                Log.e("PredictionFragment", "Error loading Excel data", ex)
                context?.let {
                    Toast.makeText(
                        it,
                        "Error loading data: ${ex.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (isAdded) {
                    tvProgress.text = "Error loading data"
                }
            }
        }
    }

    private fun loadSelectedDataSet() {
        if (!isAdded || context == null) return
        
        if (allDataSets.isEmpty()) {
            context?.let {
                Toast.makeText(it, "No data sets available", Toast.LENGTH_SHORT).show()
            }
            return
        }

        currentDataSet = spinnerDataSet.selectedItemPosition
        foodList.clear()
        foodList.addAll(allDataSets[currentDataSet])
        foodAdapter.notifyDataSetChanged()

        tvProgress.text = "Loaded Set ${currentDataSet + 1} (${foodList.size} items)"
        context?.let {
            Toast.makeText(
                it,
                "Loaded Data Set ${currentDataSet + 1} with ${foodList.size} items",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun predictAllItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded || context == null) return@launch
            
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
                    // Check if fragment is still attached
                    if (!isAdded || context == null) break
                    
                    // Update progress
                    tvProgress.text = "Predicting ${index + 1}/$totalItems..."
                    progressBar.progress = index

                    // Perform prediction in background
                    val updatedFood = withContext(Dispatchers.Default) {
                        performPrediction(food)
                    }

                    // Check again after background work
                    if (!isAdded || context == null) break
                    
                    // Update UI on main thread
                    foodAdapter.updateFood(index, updatedFood)

                    // Save to Firebase
                    Log.d("PredictionFragment", "Attempting to save prediction ${index + 1}/${foodList.size}: ${updatedFood.name}")
                    val saveResult = FirebaseRepository.saveFoodPrediction(updatedFood)
                    if (saveResult.isSuccess) {
                        Log.i("PredictionFragment", "✓ Saved ${updatedFood.name} to Firebase (ID: ${saveResult.getOrNull()})")
                    } else {
                        Log.e("PredictionFragment", "✗ Failed to save ${updatedFood.name}", saveResult.exceptionOrNull())
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
                        "F1: ${String.format("%.3f", qualityMetrics.microF1)} | Saved ✓"

                // Log detailed metrics
                Log.i("PredictionFragment", "=== PREDICTION QUALITY METRICS (Table 2) ===")
                Log.i("PredictionFragment", "Exact Match Ratio (EMR): ${String.format("%.2f", qualityMetrics.exactMatchRatio)}%")
                Log.i("PredictionFragment", "Micro F1: ${String.format("%.4f", qualityMetrics.microF1)}")
                Log.i("PredictionFragment", "Macro F1: ${String.format("%.4f", qualityMetrics.macroF1)}")

                if (isAdded && context != null) {
                    context?.let {
                        Toast.makeText(
                            it,
                            "✅ EMR: ${String.format("%.1f", qualityMetrics.exactMatchRatio)}% | " +
                                    "F1: ${String.format("%.3f", qualityMetrics.microF1)} | Saved to Firebase",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    // Notify dashboard to refresh
                    (activity as? HostActivity)?.refreshDashboard()
                }

            } catch (ex: Exception) {
                Log.e("PredictionFragment", "Error during prediction", ex)
                if (isAdded && context != null) {
                    context?.let {
                        Toast.makeText(
                            it,
                            "Prediction failed: ${ex.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    tvProgress.text = "Prediction failedf"
                }
            } finally {
                isPredicting = false
                btnPredictAll.isEnabled = true
                btnLoadDataSet.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun predictSingleItem(food: FoodData, position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!isAdded || context == null) return@launch
                
                tvProgress.text = "Predicting ${food.name}..."

                val updatedFood = withContext(Dispatchers.Default) {
                    performPrediction(food)
                }

                if (!isAdded || context == null) return@launch
                
                foodAdapter.updateFood(position, updatedFood)

                // Save to Firebase
                Log.d("PredictionFragment", "Attempting to save single prediction: ${updatedFood.name}")
                Log.d("PredictionFragment", "Model: ${updatedFood.modelName}, Dataset: ${updatedFood.datasetNumber}")
                val saveResult = FirebaseRepository.saveFoodPrediction(updatedFood)
                
                if (!isAdded || context == null) return@launch
                
                if (saveResult.isSuccess) {
                    context?.let {
                        Toast.makeText(
                            it,
                            "Saved to Firebase",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    tvProgress.text = "Prediction saved"
                } else {
                    context?.let {
                        Toast.makeText(
                            it,
                            "Failed to save to Firebase",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    tvProgress.text = "Save failed"
                }

            } catch (ex: Exception) {
                Log.e("PredictionFragment", "Error predicting item", ex)
                if (isAdded && context != null) {
                    context?.let {
                        Toast.makeText(
                            it,
                            "Prediction error: ${ex.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    tvProgress.text = "Prediction error"
                }
            }
        }
    }

    private fun performPrediction(food: FoodData): FoodData {
        val ingredients = food.ingredients
        val promptContent = buildPrompt(ingredients)
        val prompt = wrapWithChatTemplate(promptContent)

        // Get model path - use context safely
        val ctx = context
        if (ctx == null) {
            return food.copy(
                predictedAllergens = "ERROR: Context not available",
                timestamp = System.currentTimeMillis(),
                modelName = selectedModel,
                datasetNumber = currentDataSet + 1
            )
        }
        
        val modelPath = File(ctx.filesDir, selectedModel).absolutePath

        // Check if model file exists
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e("PredictionFragment", "Model file not found: $modelPath")
            return food.copy(
                predictedAllergens = "ERROR: Model file not found",
                timestamp = System.currentTimeMillis(),
                modelName = selectedModel,
                datasetNumber = currentDataSet + 1
            )
        }

        Log.i("PredictionFragment", "Using model: $modelPath (${modelFile.length() / 1024 / 1024} MB)")

        // ---- BEFORE ----
        val javaBefore = MemoryReader.javaHeapKb()
        val nativeBefore = MemoryReader.nativeHeapKb()
        val pssBefore = MemoryReader.totalPssKb()

        val startNs = System.nanoTime()
        val rawResult = inferAllergens(prompt, modelPath)
        val latencyMs = (System.nanoTime() - startNs) / 1_000_000

        // Expected format: TTFT_MS=<value>;ITPS=<value>|<output>
        val parts = rawResult.split("|", limit = 2)
        val meta = parts[0]
        val rawOutput = if (parts.size > 1) parts[1] else ""

        // Parse TTFT, ITPS, OTPS
        var ttftMs = -1L
        var itps = -1L
        var otps = -1L
        var oetMs = -1L

        meta.split(";").forEach { part ->
            when {
                part.startsWith("TTFT_MS=") -> ttftMs = part.removePrefix("TTFT_MS=").toLongOrNull() ?: -1L
                part.startsWith("ITPS=") -> itps = part.removePrefix("ITPS=").toLongOrNull() ?: -1L
                part.startsWith("OTPS=") -> otps = part.removePrefix("OTPS=").toLongOrNull() ?: -1L
                part.startsWith("OET_MS=") -> oetMs = part.removePrefix("OET_MS=").toLongOrNull() ?: -1L
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
        val ctx = requireContext()
        // Check which models are actually available
        val availableModelFiles = availableModels.filter { modelName ->
            val modelFile = File(ctx.filesDir, modelName)
            modelFile.exists()
        }

        val modelsToShow = if (availableModelFiles.isNotEmpty()) {
            availableModelFiles
        } else {
            availableModels
        }

        val adapter = ArrayAdapter(
            ctx,
            android.R.layout.simple_spinner_item,
            modelsToShow.map { it.substringBefore("-Q").substringBefore(".gguf") }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerModel.adapter = adapter

        // Set default selection
        val defaultIndex = modelsToShow.indexOfFirst { it.contains("qwen2.5-1.5b", ignoreCase = true) }
        if (defaultIndex >= 0) {
            spinnerModel.setSelection(defaultIndex)
            selectedModel = modelsToShow[defaultIndex]
        } else if (modelsToShow.isNotEmpty()) {
            spinnerModel.setSelection(0)
            selectedModel = modelsToShow[0]
        }

        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedModel = modelsToShow[position]
                Log.i("PredictionFragment", "Selected model: $selectedModel")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun copyModelIfNeeded(context: Context, modelName: String = "qwen2.5-1.5b-instruct-q4_k_m.gguf") {
        val outFile = File(context.filesDir, modelName)
        if (outFile.exists()) {
            Log.i("PredictionFragment", "Model $modelName already exists")
            return
        }
        try {
            Log.i("PredictionFragment", "Copying model $modelName from assets...")
            context.assets.open(modelName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i("PredictionFragment", "Model $modelName copied successfully")
        } catch (ex: Exception) {
            Log.w("PredictionFragment", "Model $modelName not found in assets")
        }
    }

    private fun listAvailableModels() {
        val ctx = context ?: return
        Log.i("PredictionFragment", "=== Checking available models ===")
        val modelFiles = ctx.filesDir.listFiles()?.filter { it.name.endsWith(".gguf") }
        if (modelFiles.isNullOrEmpty()) {
            Log.w("PredictionFragment", "No .gguf model files found!")
        } else {
            modelFiles.forEach { modelFile ->
                Log.i("PredictionFragment", "Found model: ${modelFile.name} (${modelFile.length() / 1024 / 1024} MB)")
            }
        }
    }

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

    private fun calculatePerSampleMacroF1(actualSet: Set<String>, predictedSet: Set<String>): Double {
        val f1Scores = allowedAllergens.map { allergen ->
            val inActual = allergen in actualSet
            val inPred = allergen in predictedSet
            val tp = if (inActual && inPred) 1 else 0
            val fp = if (!inActual && inPred) 1 else 0
            val fn = if (inActual && !inPred) 1 else 0
            if (2 * tp + fp + fn > 0) (2.0 * tp) / (2.0 * tp + fp + fn) else 0.0
        }
        return f1Scores.average()
    }

    private fun calculatePredictionQualityMetrics(groundTruth: String, predicted: String): PredictionQualityMetrics {
        val actualSet = normalizeAllergenString(groundTruth)
        val predictedSet = normalizeAllergenString(predicted)

        val tp = actualSet.intersect(predictedSet).size
        val fp = predictedSet.subtract(actualSet).size
        val fn = actualSet.subtract(predictedSet).size
        val tn = allowedAllergens.size - actualSet.size - fp

        val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        val microF1 = if (2 * tp + fp + fn > 0) (2.0 * tp) / (2.0 * tp + fp + fn) else 0.0
        val macroF1 = calculatePerSampleMacroF1(actualSet, predictedSet)
        val isExactMatch = actualSet == predictedSet
        val hammingLoss = (fp + fn).toDouble() / allowedAllergens.size
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

    private fun isAllergenInIngredients(allergen: String, ingredients: String): Boolean {
        val keywords = allergenKeywords[allergen.lowercase()] ?: return false
        val ingredientsLower = ingredients.lowercase()
        return keywords.any { ingredientsLower.contains(it) }
    }

    private fun calculateSafetyMetrics(ingredients: String, groundTruth: String, predicted: String): SafetyMetrics {
        val actualSet = normalizeAllergenString(groundTruth)
        val predictedSet = normalizeAllergenString(predicted)

        val hasHallucination = predictedSet.any { allergen ->
            allergen !in actualSet && !isAllergenInIngredients(allergen, ingredients)
        }
        val hallucinatedAllergens = predictedSet.filter { allergen ->
            allergen !in actualSet && !isAllergenInIngredients(allergen, ingredients)
        }.toList()

        val overPredicted = predictedSet.subtract(actualSet).toList()
        val hasOverPrediction = overPredicted.isNotEmpty()
        val missedAllergens = actualSet.subtract(predictedSet).toList()
        val isCorrectAbstention = if (actualSet.isEmpty()) predictedSet.isEmpty() else null

        return SafetyMetrics(
            hasHallucination = hasHallucination,
            hallucinatedAllergens = hallucinatedAllergens,
            hasOverPrediction = hasOverPrediction,
            overPredictedAllergens = overPredicted,
            isCorrectAbstention = isCorrectAbstention,
            missedAllergens = missedAllergens
        )
    }

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
        val microF1 = if (2 * totalTP + totalFP + totalFN > 0) (2.0 * totalTP) / (2.0 * totalTP + totalFP + totalFN) else 0.0
        val emr = (exactMatches.toDouble() / n) * 100.0
        val fnr = if (totalTP + totalFN > 0) totalFN.toDouble() / (totalTP + totalFN) else 0.0

        return AggregatedQualityMetrics(
            totalTP = totalTP,
            totalFP = totalFP,
            totalFN = totalFN,
            totalTN = totalTN,
            avgPrecision = sumPrecision / n,
            avgRecall = sumRecall / n,
            microF1 = microF1,
            macroF1 = sumMacroF1 / n,
            exactMatchRatio = emr,
            avgHammingLoss = sumHammingLoss / n,
            falseNegativeRate = fnr,
            totalSamples = n,
            exactMatches = exactMatches
        )
    }

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
        return AggregatedSafetyMetrics(
            hallucinationRate = (hallucinationCount.toDouble() / n) * 100.0,
            overPredictionRate = (overPredictionCount.toDouble() / n) * 100.0,
            abstentionAccuracy = if (abstentionCases > 0) (correctAbstentionCount.toDouble() / abstentionCases) * 100.0 else 100.0,
            totalSamples = n,
            hallucinationCount = hallucinationCount,
            overPredictionCount = overPredictionCount,
            correctAbstentionCount = correctAbstentionCount,
            abstentionCases = abstentionCases
        )
    }
}

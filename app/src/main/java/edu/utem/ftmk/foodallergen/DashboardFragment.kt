package edu.utem.ftmk.foodallergen

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.AdapterView
import android.widget.TableLayout
import android.widget.TableRow
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * BITP 3453 Mobile Application Development
 * Semester 1, 2025/2026
 *
 * @author Emaliana Kasmuri, FTMK, UTeM
 *
 * Purpose:
 * Dashboard Fragment - Displays structured metrics in tabular format
 * for comparative analysis and project reports.
 */
class DashboardFragment : Fragment() {

    // 9 standard allergen categories
    private val allAllergens = listOf(
        "milk", "egg", "peanut", "tree nut", "wheat", "soy", "fish", "shellfish", "sesame"
    )

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

    // Views
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var spinnerModelFilter: Spinner

    // Metric TextViews - Quality
    private lateinit var tvTotalSamples: TextView
    private lateinit var tvPrecision: TextView
    private lateinit var tvRecall: TextView
    private lateinit var tvMicroF1: TextView
    private lateinit var tvMacroF1: TextView
    private lateinit var tvEMR: TextView
    private lateinit var tvHammingLoss: TextView
    private lateinit var tvFNR: TextView

    // Metric TextViews - Safety
    private lateinit var tvHallucination: TextView
    private lateinit var tvOverPrediction: TextView
    private lateinit var tvAbstention: TextView

    // Metric TextViews - Efficiency
    private lateinit var tvLatency: TextView
    private lateinit var tvTTFT: TextView
    private lateinit var tvITPS: TextView
    private lateinit var tvOTPS: TextView
    private lateinit var tvOET: TextView
    private lateinit var tvJavaHeap: TextView
    private lateinit var tvNativeHeap: TextView
    private lateinit var tvMemory: TextView

    // Confusion Matrix
    private lateinit var tvTP: TextView
    private lateinit var tvFP: TextView
    private lateinit var tvFN: TextView
    private lateinit var tvTN: TextView

    // Model Comparison
    private lateinit var cardModelComparison: MaterialCardView
    private lateinit var tvRecommendedModel: TextView
    private lateinit var tvRecommendationReason: TextView
    private lateinit var tableModelComparison: TableLayout

    private var dataByModel: Map<String, List<FoodData>> = emptyMap()
    private var currentModel: String = "All Models"
    private var isDataLoaded: Boolean = false

    /**
     * Data class to hold model comparison metrics
     */
    data class ModelScore(
        val modelName: String,
        val shortName: String,
        val microF1: Double,
        val recall: Double,
        val safetyScore: Double,  // 100 - hallucinationRate
        val overallScore: Double,
        val sampleCount: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        loadDashboardData()
    }

    private fun initViews(view: View) {
        btnRefresh = view.findViewById(R.id.btnRefresh)
        btnExport = view.findViewById(R.id.btnExport)
        spinnerModelFilter = view.findViewById(R.id.spinnerModelFilter)

        // Quality metrics
        tvTotalSamples = view.findViewById(R.id.tvTotalSamples)
        tvPrecision = view.findViewById(R.id.tvPrecision)
        tvRecall = view.findViewById(R.id.tvRecall)
        tvMicroF1 = view.findViewById(R.id.tvMicroF1)
        tvMacroF1 = view.findViewById(R.id.tvMacroF1)
        tvEMR = view.findViewById(R.id.tvEMR)
        tvHammingLoss = view.findViewById(R.id.tvHammingLoss)
        tvFNR = view.findViewById(R.id.tvFNR)

        // Safety metrics
        tvHallucination = view.findViewById(R.id.tvHallucination)
        tvOverPrediction = view.findViewById(R.id.tvOverPrediction)
        tvAbstention = view.findViewById(R.id.tvAbstention)

        // Efficiency metrics
        tvLatency = view.findViewById(R.id.tvLatency)
        tvTTFT = view.findViewById(R.id.tvTTFT)
        tvITPS = view.findViewById(R.id.tvITPS)
        tvOTPS = view.findViewById(R.id.tvOTPS)
        tvOET = view.findViewById(R.id.tvOET)
        tvJavaHeap = view.findViewById(R.id.tvJavaHeap)
        tvNativeHeap = view.findViewById(R.id.tvNativeHeap)
        tvMemory = view.findViewById(R.id.tvMemory)

        // Confusion matrix
        tvTP = view.findViewById(R.id.tvTP)
        tvFP = view.findViewById(R.id.tvFP)
        tvFN = view.findViewById(R.id.tvFN)
        tvTN = view.findViewById(R.id.tvTN)

        // Model comparison
        cardModelComparison = view.findViewById(R.id.cardModelComparison)
        tvRecommendedModel = view.findViewById(R.id.tvRecommendedModel)
        tvRecommendationReason = view.findViewById(R.id.tvRecommendationReason)
        tableModelComparison = view.findViewById(R.id.tableModelComparison)
    }

    private fun setupListeners() {
        // Disable export button initially until data is loaded
        btnExport.isEnabled = false
        
        btnRefresh.setOnClickListener {
            loadDashboardData()
        }

        btnExport.setOnClickListener {
            if (isDataLoaded && dataByModel.isNotEmpty()) {
                exportResults()
            } else {
                android.widget.Toast.makeText(
                    requireContext(),
                    "No data available to export. Please wait for data to load.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        spinnerModelFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedItem = parent?.getItemAtPosition(position).toString()
                currentModel = selectedItem
                updateMetricsDisplay()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    fun loadDashboardData() {
        // Check if view is available before accessing viewLifecycleOwner
        if (!isAdded || view == null) {
            Log.w("DashboardFragment", "Cannot load data - Fragment view not available")
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("DashboardFragment", "loadDashboardData called")
                
                // Double-check view is still available after coroutine launch
                if (!isAdded || view == null) {
                    Log.w("DashboardFragment", "View destroyed after coroutine launch")
                    return@launch
                }
                
                tvTotalSamples.text = "Loading..."
                btnExport.isEnabled = false
                isDataLoaded = false

                val result = FirebaseRepository.getAllPredictionsGroupedByModel()

                if (result.isFailure) {
                    Log.e("DashboardFragment", "Failed to load data", result.exceptionOrNull())
                    tvTotalSamples.text = "Error"
                    btnExport.isEnabled = false
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Failed to load data: ${result.exceptionOrNull()?.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                dataByModel = result.getOrNull() ?: emptyMap()
                Log.d("DashboardFragment", "Loaded data for ${dataByModel.size} models")

                if (dataByModel.isEmpty()) {
                    Log.w("DashboardFragment", "No data found in Firestore")
                    tvTotalSamples.text = "No data"
                    btnExport.isEnabled = false
                    android.widget.Toast.makeText(
                        requireContext(),
                        "No predictions found. Please run predictions first.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Setup model filter spinner
                val modelOptions = mutableListOf("All Models")
                modelOptions.addAll(dataByModel.keys.map { 
                    it.substringBefore("-Q").substringBefore(".gguf") 
                })
                Log.d("DashboardFragment", "Model options: $modelOptions")

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    modelOptions
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerModelFilter.adapter = adapter

                isDataLoaded = true
                btnExport.isEnabled = true
                updateMetricsDisplay()
                
                // Update model comparison table
                updateModelComparison()

            } catch (e: Exception) {
                Log.e("DashboardFragment", "Error loading dashboard", e)
                tvTotalSamples.text = "Error"
                btnExport.isEnabled = false
                isDataLoaded = false
                android.widget.Toast.makeText(
                    requireContext(),
                    "Error loading dashboard: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateMetricsDisplay() {
        Log.d("DashboardFragment", "updateMetricsDisplay called, currentModel=$currentModel")
        
        if (dataByModel.isEmpty()) {
            Log.w("DashboardFragment", "dataByModel is empty")
            setEmptyState()
            return
        }

        // Get food list based on selected model
        val foodList = if (currentModel == "All Models") {
            dataByModel.values.flatten()
        } else {
            // Find the matching model by short name
            dataByModel.entries.find { 
                it.key.substringBefore("-Q").substringBefore(".gguf") == currentModel 
            }?.value ?: emptyList()
        }

        Log.d("DashboardFragment", "Food list size: ${foodList.size}")
        
        if (foodList.isEmpty()) {
            Log.w("DashboardFragment", "Food list is empty after filtering")
            setEmptyState()
            return
        }

        calculateAndDisplayMetrics(foodList)
    }

    private fun setEmptyState() {
        tvTotalSamples.text = "0"
        tvPrecision.text = "--"
        tvRecall.text = "--"
        tvMicroF1.text = "--"
        tvMacroF1.text = "--"
        tvEMR.text = "--"
        tvHammingLoss.text = "--"
        tvFNR.text = "--"
        tvHallucination.text = "--"
        tvOverPrediction.text = "--"
        tvAbstention.text = "--"
        tvLatency.text = "--"
        tvTTFT.text = "--"
        tvITPS.text = "--"
        tvOTPS.text = "--"
        tvOET.text = "--"
        tvJavaHeap.text = "--"
        tvNativeHeap.text = "--"
        tvMemory.text = "--"
        tvTP.text = "0"
        tvFP.text = "0"
        tvFN.text = "0"
        tvTN.text = "0"
    }

    private fun calculateAndDisplayMetrics(foodList: List<FoodData>) {
        Log.d("DashboardFragment", "calculateAndDisplayMetrics called with ${foodList.size} items")
        
        // Aggregate confusion matrix counts from stored data
        var totalTP = 0
        var totalFP = 0
        var totalFN = 0
        var totalTN = 0
        var exactMatches = 0
        var sumLatency = 0L
        var sumTTFT = 0L
        var sumITPS = 0L
        var sumOTPS = 0L
        var sumOET = 0L
        var sumJavaHeap = 0L
        var sumNativeHeap = 0L
        var sumMemory = 0L

        // For F1 Macro calculation - track per-allergen counts
        val perAllergenCounts = allAllergens.associateWith {
            mutableMapOf("tp" to 0, "fp" to 0, "fn" to 0, "tn" to 0)
        }.toMutableMap()

        // Safety metrics accumulators
        var hallucinationCount = 0
        var overPredictionCount = 0
        var abstentionTotal = 0
        var abstentionCorrect = 0

        foodList.forEach { food ->
            // Aggregate confusion matrix from stored counts
            food.qualityMetrics?.let { qm ->
                totalTP += qm.truePositives
                totalFP += qm.falsePositives
                totalFN += qm.falseNegatives
                totalTN += qm.trueNegatives
                if (qm.isExactMatch) exactMatches++
            }

            food.metrics?.let { m ->
                sumLatency += m.latencyMs
                sumTTFT += m.ttft
                sumITPS += m.itps
                sumOTPS += m.otps
                sumOET += m.oet
                sumJavaHeap += m.javaHeapKb
                sumNativeHeap += m.nativeHeapKb
                sumMemory += m.totalPssKb
            }

            // Calculate per-allergen counts for F1 Macro
            val predSet = normalizeAllergenString(food.predictedAllergens)
            val truthSet = normalizeAllergenString(food.allergensMapped)

            allAllergens.forEach { allergen ->
                val inPred = allergen in predSet
                val inTruth = allergen in truthSet
                when {
                    inPred && inTruth -> perAllergenCounts[allergen]!!["tp"] = perAllergenCounts[allergen]!!["tp"]!! + 1
                    inPred && !inTruth -> perAllergenCounts[allergen]!!["fp"] = perAllergenCounts[allergen]!!["fp"]!! + 1
                    !inPred && inTruth -> perAllergenCounts[allergen]!!["fn"] = perAllergenCounts[allergen]!!["fn"]!! + 1
                    else -> perAllergenCounts[allergen]!!["tn"] = perAllergenCounts[allergen]!!["tn"]!! + 1
                }
            }

            // Calculate safety metrics on-the-fly
            if (hasHallucination(food.predictedAllergens, food.ingredients)) {
                hallucinationCount++
            }
            if (hasOverPrediction(food.predictedAllergens, food.allergensMapped)) {
                overPredictionCount++
            }

            // Abstention accuracy
            val groundTruthEmpty = truthSet.isEmpty()
            if (groundTruthEmpty) {
                abstentionTotal++
                if (predSet.isEmpty()) {
                    abstentionCorrect++
                }
            }
        }

        val n = foodList.size

        // Calculate metrics on-the-fly from aggregated counts
        val precision = if (totalTP + totalFP > 0) totalTP.toDouble() / (totalTP + totalFP) else 0.0
        val recall = if (totalTP + totalFN > 0) totalTP.toDouble() / (totalTP + totalFN) else 0.0

        // Micro F1: 2TP / (2TP + FP + FN)
        val microF1 = if (2 * totalTP + totalFP + totalFN > 0) {
            (2.0 * totalTP) / (2.0 * totalTP + totalFP + totalFN)
        } else 0.0

        // Macro F1: Average of per-allergen F1 scores
        val macroF1 = calculateF1Macro(perAllergenCounts)

        // EMR: Exact Match Ratio
        val emr = if (n > 0) (exactMatches.toDouble() / n) * 100.0 else 0.0

        // Hamming Loss: (FP + FN) / (N × L)
        val hammingLoss = if (n > 0) (totalFP + totalFN).toDouble() / (n * 9) else 0.0

        // FNR: FN / (TP + FN)
        val fnr = if (totalTP + totalFN > 0) totalFN.toDouble() / (totalTP + totalFN) else 0.0

        // Safety metrics
        val hallucinationRate = if (n > 0) (hallucinationCount.toDouble() / n) * 100.0 else 0.0
        val overPredictionRate = if (n > 0) (overPredictionCount.toDouble() / n) * 100.0 else 0.0
        val abstentionAccuracy = if (abstentionTotal > 0) (abstentionCorrect.toDouble() / abstentionTotal) * 100.0 else 100.0

        val avgLatency = if (n > 0) sumLatency / n else 0L
        val avgTTFT = if (n > 0) sumTTFT / n else 0L
        val avgITPS = if (n > 0) sumITPS / n else 0L
        val avgOTPS = if (n > 0) sumOTPS / n else 0L
        val avgOET = if (n > 0) sumOET / n else 0L
        val avgJavaHeap = if (n > 0) sumJavaHeap / n else 0L
        val avgNativeHeap = if (n > 0) sumNativeHeap / n else 0L
        val avgMemory = if (n > 0) sumMemory / n else 0L

        // Update UI
        tvTotalSamples.text = n.toString()

        // Quality metrics
        tvPrecision.text = String.format("%.4f", precision)
        tvRecall.text = String.format("%.4f", recall)
        tvMicroF1.text = String.format("%.2f%%", microF1 * 100)
        tvMacroF1.text = String.format("%.2f%%", macroF1 * 100)
        tvEMR.text = String.format("%.2f%%", emr)
        tvHammingLoss.text = String.format("%.4f", hammingLoss)
        tvFNR.text = String.format("%.2f%%", fnr * 100)

        // Safety metrics
        tvHallucination.text = String.format("%.2f%%", hallucinationRate)
        tvOverPrediction.text = String.format("%.2f%%", overPredictionRate)
        tvAbstention.text = String.format("%.2f%%", abstentionAccuracy)

        // Efficiency metrics
        tvLatency.text = String.format("%d ms", avgLatency)
        tvTTFT.text = String.format("%d ms", avgTTFT)
        tvITPS.text = String.format("%d tok/s", avgITPS)
        tvOTPS.text = String.format("%d tok/s", avgOTPS)
        tvOET.text = String.format("%d ms", avgOET)
        tvJavaHeap.text = String.format("%d KB", avgJavaHeap)
        tvNativeHeap.text = String.format("%d KB", avgNativeHeap)
        tvMemory.text = String.format("%d KB", avgMemory)

        // Confusion matrix
        tvTP.text = totalTP.toString()
        tvFP.text = totalFP.toString()
        tvFN.text = totalFN.toString()
        tvTN.text = totalTN.toString()
    }

    private fun calculateF1Macro(perAllergenCounts: Map<String, Map<String, Int>>): Double {
        val f1Scores = perAllergenCounts.values.map { counts ->
            val tp = counts["tp"] ?: 0
            val fp = counts["fp"] ?: 0
            val fn = counts["fn"] ?: 0
            val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
            val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
            if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
        }
        return if (f1Scores.isNotEmpty()) f1Scores.average() else 0.0
    }

    private fun isAllergenInIngredients(allergen: String, ingredients: String): Boolean {
        val keywords = allergenKeywords[allergen.lowercase()] ?: return false
        val ingredientsLower = ingredients.lowercase()
        return keywords.any { ingredientsLower.contains(it) }
    }

    private fun hasHallucination(predictedAllergens: String, ingredients: String): Boolean {
        val predicted = normalizeAllergenString(predictedAllergens)
        if (predicted.isEmpty()) return false
        return predicted.any { !isAllergenInIngredients(it, ingredients) }
    }

    private fun hasOverPrediction(predictedAllergens: String, groundTruth: String): Boolean {
        val predSet = normalizeAllergenString(predictedAllergens)
        val truthSet = normalizeAllergenString(groundTruth)
        return (predSet - truthSet).isNotEmpty()
    }

    private fun normalizeAllergenString(allergens: String): Set<String> {
        if (allergens.isBlank() || allergens.equals("EMPTY", ignoreCase = true)) {
            return emptySet()
        }
        return allergens.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "empty" && it != "none" }
            .toSet()
    }

    private fun exportResults() {
        if (!isDataLoaded || dataByModel.isEmpty()) {
            android.widget.Toast.makeText(
                requireContext(),
                "No data available to export. Please wait for data to load.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                btnExport.isEnabled = false
                android.widget.Toast.makeText(
                    requireContext(),
                    "Exporting data to Excel...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                val fetchResult = FirebaseRepository.getAllPredictionsGroupedByModel()

                if (fetchResult.isFailure) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Export failed: ${fetchResult.exceptionOrNull()?.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    btnExport.isEnabled = true
                    return@launch
                }

                val data = fetchResult.getOrNull() ?: emptyMap()
                if (data.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "No data to export",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    btnExport.isEnabled = true
                    return@launch
                }

                val exportResult = ExcelExporter.exportToExcel(requireContext(), data)

                if (exportResult.isSuccess) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "✅ Exported to: ${exportResult.getOrNull()}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "❌ Export failed: ${exportResult.exceptionOrNull()?.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "❌ Export error: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } finally {
                btnExport.isEnabled = true
            }
        }
    }

    /**
     * Updates the model comparison table with metrics from all models
     */
    private fun updateModelComparison() {
        if (dataByModel.isEmpty()) {
            // Need at least 1 model to show ranking
            cardModelComparison.visibility = View.GONE
            return
        }
        
        cardModelComparison.visibility = View.VISIBLE
        
        // Calculate metrics for each model
        val modelScores = mutableListOf<ModelScore>()
        
        dataByModel.forEach { (modelName, foodList) ->
            if (foodList.isNotEmpty()) {
                val score = calculateModelScore(modelName, foodList)
                modelScores.add(score)
            }
        }
        
        if (modelScores.isEmpty()) {
            cardModelComparison.visibility = View.GONE
            return
        }
        
        // Sort by overall score (highest first)
        modelScores.sortByDescending { it.overallScore }
        
        // Update recommended model
        val bestModel = modelScores.first()
        tvRecommendedModel.text = "🏆 ${bestModel.shortName}"
        
        // Generate recommendation reason
        val reason = buildRecommendationReason(bestModel, modelScores)
        tvRecommendationReason.text = reason
        
        // Update comparison table
        updateComparisonTable(modelScores)
    }

    /**
     * Calculate comprehensive score for a model
     */
    private fun calculateModelScore(modelName: String, foodList: List<FoodData>): ModelScore {
        var totalTP = 0
        var totalFP = 0
        var totalFN = 0
        var hallucinationCount = 0
        
        val perAllergenCounts = allAllergens.associateWith {
            mutableMapOf("tp" to 0, "fp" to 0, "fn" to 0, "tn" to 0)
        }.toMutableMap()
        
        foodList.forEach { food ->
            food.qualityMetrics?.let { qm ->
                totalTP += qm.truePositives
                totalFP += qm.falsePositives
                totalFN += qm.falseNegatives
            }
            
            val predSet = normalizeAllergenString(food.predictedAllergens)
            val truthSet = normalizeAllergenString(food.allergensMapped)
            
            allAllergens.forEach { allergen ->
                val inPred = allergen in predSet
                val inTruth = allergen in truthSet
                when {
                    inPred && inTruth -> perAllergenCounts[allergen]!!["tp"] = perAllergenCounts[allergen]!!["tp"]!! + 1
                    inPred && !inTruth -> perAllergenCounts[allergen]!!["fp"] = perAllergenCounts[allergen]!!["fp"]!! + 1
                    !inPred && inTruth -> perAllergenCounts[allergen]!!["fn"] = perAllergenCounts[allergen]!!["fn"]!! + 1
                    else -> perAllergenCounts[allergen]!!["tn"] = perAllergenCounts[allergen]!!["tn"]!! + 1
                }
            }
            
            if (hasHallucination(food.predictedAllergens, food.ingredients)) {
                hallucinationCount++
            }
        }
        
        val n = foodList.size
        
        // Calculate metrics
        val microF1 = if (2 * totalTP + totalFP + totalFN > 0) {
            (2.0 * totalTP) / (2.0 * totalTP + totalFP + totalFN)
        } else 0.0
        
        val recall = if (totalTP + totalFN > 0) {
            totalTP.toDouble() / (totalTP + totalFN)
        } else 0.0
        
        val hallucinationRate = if (n > 0) (hallucinationCount.toDouble() / n) * 100.0 else 0.0
        val safetyScore = 100.0 - hallucinationRate
        
        // Overall score: Weighted combination
        // - Recall (40%): Critical for allergen safety - must not miss allergens
        // - Micro F1 (35%): Overall prediction quality
        // - Safety (25%): Avoid hallucinations
        val overallScore = (recall * 40) + (microF1 * 35) + (safetyScore / 100.0 * 25)
        
        val shortName = modelName.substringBefore("-Q").substringBefore(".gguf")
        
        return ModelScore(
            modelName = modelName,
            shortName = shortName,
            microF1 = microF1,
            recall = recall,
            safetyScore = safetyScore,
            overallScore = overallScore,
            sampleCount = n
        )
    }

    /**
     * Build recommendation reason text
     */
    private fun buildRecommendationReason(best: ModelScore, all: List<ModelScore>): String {
        // If only one model, show its metrics
        if (all.size == 1) {
            return "✓ Recall: ${String.format("%.1f%%", best.recall * 100)} | F1: ${String.format("%.1f%%", best.microF1 * 100)} | Score: ${String.format("%.1f", best.overallScore)}/100"
        }
        
        val reasons = mutableListOf<String>()
        
        // Check if best in each category
        val bestF1 = all.maxByOrNull { it.microF1 }
        val bestRecall = all.maxByOrNull { it.recall }
        val bestSafety = all.maxByOrNull { it.safetyScore }
        
        if (best == bestRecall) {
            reasons.add("highest recall (${String.format("%.1f%%", best.recall * 100)})")
        }
        if (best == bestF1) {
            reasons.add("best F1 score (${String.format("%.1f%%", best.microF1 * 100)})")
        }
        if (best == bestSafety) {
            reasons.add("lowest hallucination rate")
        }
        
        return if (reasons.isNotEmpty()) {
            "✓ ${reasons.joinToString(", ")} | Score: ${String.format("%.1f", best.overallScore)}/100"
        } else {
            "✓ Best overall balance across metrics | Score: ${String.format("%.1f", best.overallScore)}/100"
        }
    }

    /**
     * Update the comparison table with model data - showing all models with their rank
     */
    private fun updateComparisonTable(scores: List<ModelScore>) {
        // Remove all rows except header (index 0)
        while (tableModelComparison.childCount > 1) {
            tableModelComparison.removeViewAt(1)
        }
        
        val context = requireContext()
        val bestScore = scores.firstOrNull()
        
        // Add all models with their rank
        scores.forEachIndexed { index, score ->
            val rank = index + 1 // 1-based ranking
            val row = TableRow(context).apply {
                if (index % 2 == 0) {
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
                } else {
                    setBackgroundColor(0xFFF5F5F5.toInt())
                }
                setPadding(4, 8, 4, 8)
            }
            
            // Rank column with medal emoji for top 3
            val rankText = when (rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "#$rank"
            }
            row.addView(createTableCell(context, rankText, Typeface.BOLD, 10f, true))
            
            // Model name column
            val isBest = score == bestScore
            row.addView(createTableCell(context, score.shortName, 
                if (isBest) Typeface.BOLD else Typeface.NORMAL, 
                10f))
            
            // F1 column
            val f1Text = String.format("%.1f%%", score.microF1 * 100)
            row.addView(createTableCell(context, f1Text, Typeface.NORMAL, 10f, true))
            
            // Recall column
            val recallText = String.format("%.1f%%", score.recall * 100)
            row.addView(createTableCell(context, recallText, Typeface.NORMAL, 10f, true))
            
            // Safety column
            val safetyText = String.format("%.0f%%", score.safetyScore)
            row.addView(createTableCell(context, safetyText, Typeface.NORMAL, 10f, true))
            
            // Overall score column
            val scoreText = String.format("%.1f", score.overallScore)
            val scoreColor = when {
                score.overallScore >= 70 -> 0xFF2E7D32.toInt() // Green
                score.overallScore >= 50 -> 0xFFFF9800.toInt() // Orange
                else -> 0xFFC62828.toInt() // Red
            }
            row.addView(createTableCell(context, scoreText, Typeface.BOLD, 10f, true, scoreColor))
            
            tableModelComparison.addView(row)
        }
    }

    /**
     * Helper to create table cells
     */
    private fun createTableCell(
        context: android.content.Context, 
        text: String, 
        typeface: Int = Typeface.NORMAL,
        textSize: Float = 12f,
        center: Boolean = false,
        textColor: Int? = null
    ): TextView {
        return TextView(context).apply {
            this.text = text
            this.setTypeface(null, typeface)
            this.textSize = textSize
            this.setPadding(6, 6, 6, 6)
            if (center) {
                this.gravity = android.view.Gravity.CENTER
            }
            textColor?.let { this.setTextColor(it) }
        }
    }
}

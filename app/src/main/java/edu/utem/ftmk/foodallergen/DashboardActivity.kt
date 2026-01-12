package edu.utem.ftmk.foodallergen

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * BITP 3453 Mobile Application Development
 * Semester 1, 2025/2026
 *
 * @author Emaliana Kasmuri, FTMK, UTeM
 *
 * Purpose:
 * Model Performance Dashboard - Displays side-by-side comparison of all models
 * Shows aggregated metrics from Tables 2, 3, and 4
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var tvDashboardContent: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnExport: Button
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        tvDashboardContent = findViewById(R.id.tvDashboardContent)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnExport = findViewById(R.id.btnExport)

        btnRefresh.setOnClickListener {
            loadDashboardData()
        }

        btnExport.setOnClickListener {
            exportResults()
        }

        loadDashboardData()
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            try {
                tvDashboardContent.text = "Loading dashboard data from Firebase..."

                // Use the new method from FirebaseRepository
                val result = FirebaseRepository.getAllPredictionsGroupedByModel()
                
                if (result.isFailure) {
                    tvDashboardContent.text = "Error: ${result.exceptionOrNull()?.message}"
                    return@launch
                }
                
                val dataByModel = result.getOrNull() ?: emptyMap()

                // Build dashboard display using aggregation functions
                val dashboard = buildDashboard(dataByModel)
                tvDashboardContent.text = dashboard

            } catch (e: Exception) {
                tvDashboardContent.text = "Error loading dashboard: ${e.message}"
            }
        }
    }

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

    private fun buildDashboard(dataByModel: Map<String, List<FoodData>>): String {
        val sb = StringBuilder()

        sb.append("═══════════════════════════════════════\n")
        sb.append("   MODEL PERFORMANCE DASHBOARD\n")
        sb.append("═══════════════════════════════════════\n\n")

        if (dataByModel.isEmpty()) {
            sb.append("No data available. Run predictions first.\n")
            return sb.toString()
        }

        dataByModel.forEach { (modelName, foodList) ->
            val modelDisplayName = modelName.substringBefore("-Q").substringBefore(".gguf")

            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("📱 MODEL: $modelDisplayName\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("Total Samples: ${foodList.size}\n\n")

            // Aggregate confusion matrix counts from stored data
            var totalTP = 0
            var totalFP = 0
            var totalFN = 0
            var totalTN = 0
            var exactMatches = 0
            var sumLatency = 0L
            var sumTTFT = 0L
            
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

            // Display metrics
            sb.append("📊 QUALITY METRICS (Table 2)\n")
            sb.append("─────────────────────────────────────\n")
            sb.append("Precision: %.4f\n".format(precision))
            sb.append("Recall: %.4f\n".format(recall))
            sb.append("Micro-F1: %.2f%%\n".format(microF1 * 100))
            sb.append("Macro-F1: %.2f%%\n".format(macroF1 * 100))
            sb.append("Exact Match Ratio: %.2f%%\n".format(emr))
            sb.append("Hamming Loss: %.4f\n".format(hammingLoss))
            sb.append("False Negative Rate: %.2f%%\n\n".format(fnr * 100))

            sb.append("⚠️ SAFETY METRICS (Table 3)\n")
            sb.append("─────────────────────────────────────\n")
            sb.append("Hallucination Rate: %.2f%%\n".format(hallucinationRate))
            sb.append("Over-Prediction Rate: %.2f%%\n".format(overPredictionRate))
            sb.append("Abstention Accuracy: %.2f%%\n\n".format(abstentionAccuracy))

            sb.append("⚡ PERFORMANCE METRICS (Table 4)\n")
            sb.append("─────────────────────────────────────\n")
            sb.append("Avg Latency: %d ms\n".format(avgLatency))
            sb.append("Avg TTFT: %d ms\n\n".format(avgTTFT))
        }

        sb.append("═══════════════════════════════════════\n")
        return sb.toString()
    }
    
    /**
     * Calculate F1 Macro: average of per-allergen F1 scores
     */
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
    
    /**
     * Check if allergen keywords exist in ingredients
     */
    private fun isAllergenInIngredients(allergen: String, ingredients: String): Boolean {
        val keywords = allergenKeywords[allergen.lowercase()] ?: return false
        val ingredientsLower = ingredients.lowercase()
        return keywords.any { ingredientsLower.contains(it) }
    }
    
    /**
     * Check for hallucinations (predicted allergens not derivable from ingredients)
     */
    private fun hasHallucination(predictedAllergens: String, ingredients: String): Boolean {
        val predicted = normalizeAllergenString(predictedAllergens)
        if (predicted.isEmpty()) return false
        return predicted.any { !isAllergenInIngredients(it, ingredients) }
    }
    
    /**
     * Check for over-prediction (extra allergens beyond ground truth)
     */
    private fun hasOverPrediction(predictedAllergens: String, groundTruth: String): Boolean {
        val predSet = normalizeAllergenString(predictedAllergens)
        val truthSet = normalizeAllergenString(groundTruth)
        return (predSet - truthSet).isNotEmpty()
    }
    
    /**
     * Normalize allergen string for comparison
     */
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
        lifecycleScope.launch {
            try {
                // Use FirebaseRepository to fetch all predictions
                val fetchResult = FirebaseRepository.getAllPredictionsGroupedByModel()
                
                if (fetchResult.isFailure) {
                    tvDashboardContent.append("\n\n❌ Export failed: ${fetchResult.exceptionOrNull()?.message}")
                    return@launch
                }
                
                val dataByModel = fetchResult.getOrNull() ?: emptyMap()

                val exportResult = ExcelExporter.exportToExcel(this@DashboardActivity, dataByModel)

                if (exportResult.isSuccess) {
                    tvDashboardContent.append("\n\n✅ Exported to: ${exportResult.getOrNull()}")
                } else {
                    tvDashboardContent.append("\n\n❌ Export failed: ${exportResult.exceptionOrNull()?.message}")
                }

            } catch (e: Exception) {
                tvDashboardContent.append("\n\n❌ Export error: ${e.message}")
            }
        }
    }
}

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

            // Calculate aggregated metrics
            var totalTP = 0
            var totalFP = 0
            var totalFN = 0
            var totalTN = 0
            var exactMatches = 0
            var sumPrecision = 0.0
            var sumRecall = 0.0
            var sumHammingLoss = 0.0
            var sumLatency = 0L
            var sumTTFT = 0L

            var hallucinationCount = 0
            var overPredictionCount = 0

            foodList.forEach { food ->
                food.qualityMetrics?.let { qm ->
                    totalTP += qm.truePositives
                    totalFP += qm.falsePositives
                    totalFN += qm.falseNegatives
                    totalTN += qm.trueNegatives
                    if (qm.isExactMatch) exactMatches++
                    sumPrecision += qm.precision
                    sumRecall += qm.recall
                    sumHammingLoss += qm.hammingLoss
                }
                
                food.safetyMetrics?.let { sm ->
                    if (sm.hasHallucination) hallucinationCount++
                    if (sm.hasOverPrediction) overPredictionCount++
                }
                
                food.metrics?.let { m ->
                    sumLatency += m.latencyMs
                    sumTTFT += m.ttft
                }
            }

            val n = foodList.size
            val avgPrecision = if (n > 0) sumPrecision / n else 0.0
            val avgRecall = if (n > 0) sumRecall / n else 0.0

            val microF1 = if (2 * totalTP + totalFP + totalFN > 0) {
                (2.0 * totalTP) / (2.0 * totalTP + totalFP + totalFN)
            } else 0.0

            val macroF1 = if (avgPrecision + avgRecall > 0) {
                2.0 * (avgPrecision * avgRecall) / (avgPrecision + avgRecall)
            } else 0.0

            val emr = if (n > 0) (exactMatches.toDouble() / n) * 100.0 else 0.0
            val avgHammingLoss = if (n > 0) sumHammingLoss / n else 0.0

            val fnr = if (totalTP + totalFN > 0) {
                totalFN.toDouble() / (totalTP + totalFN)
            } else 0.0
            
            val hallucinationRate = if (n > 0) (hallucinationCount.toDouble() / n) * 100.0 else 0.0
            val overPredictionRate = if (n > 0) (overPredictionCount.toDouble() / n) * 100.0 else 0.0
            
            val avgLatency = if (n > 0) sumLatency / n else 0L
            val avgTTFT = if (n > 0) sumTTFT / n else 0L

            // Display metrics
            sb.append("📊 QUALITY METRICS (Table 2)\n")
            sb.append("─────────────────────────────────────\n")
            sb.append("Micro-F1: %.2f%%\n".format(microF1 * 100))
            sb.append("Macro-F1: %.2f%%\n".format(macroF1 * 100))
            sb.append("Exact Match Ratio: %.2f%%\n".format(emr))
            sb.append("Avg Hamming Loss: %.4f\n".format(avgHammingLoss))
            sb.append("False Negative Rate: %.2f%%\n\n".format(fnr * 100))

            sb.append("⚠️ SAFETY METRICS (Table 3)\n")
            sb.append("─────────────────────────────────────\n")
            sb.append("Hallucination Rate: %.2f%%\n".format(hallucinationRate))
            sb.append("Over-Prediction Rate: %.2f%%\n\n".format(overPredictionRate))

            sb.append("⚡ PERFORMANCE METRICS (Table 4)\n")
            sb.append("─────────────────────────────────────\n")
            sb.append("Avg Latency: %d ms\n".format(avgLatency))
            sb.append("Avg TTFT: %d ms\n\n".format(avgTTFT))
        }

        sb.append("═══════════════════════════════════════\n")
        return sb.toString()
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

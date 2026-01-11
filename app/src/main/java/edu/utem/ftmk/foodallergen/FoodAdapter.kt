package edu.utem.ftmk.foodallergen

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * BITP 3453 Mobile Application Development
 * Semester 1 2025/2026
 *
 * @author Emaliana Kasmuri, FTMK, UTeM
 *
 * Purpose:
 * RecyclerView adapter for displaying food items with prediction results.
 * 
 * Features:
 * - Displays food name, ingredients, and allergen information
 * - Color-codes predictions (green=correct, orange=incorrect)
 * - Shows performance metrics including model name
 * - Supports click handling for individual predictions
 */
class FoodAdapter(
    private val foodList: MutableList<FoodData>,
    private val onPredictClick: (FoodData, Int) -> Unit
) : RecyclerView.Adapter<FoodAdapter.FoodViewHolder>() {

    inner class FoodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFoodName: TextView = itemView.findViewById(R.id.tvFoodName)
        val tvIngredients: TextView = itemView.findViewById(R.id.tvIngredients)
        val tvMappedAllergens: TextView = itemView.findViewById(R.id.tvMappedAllergens)
        val tvPredictedAllergens: TextView = itemView.findViewById(R.id.tvPredictedAllergens)
        val tvMetrics: TextView = itemView.findViewById(R.id.tvMetrics)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food, parent, false)
        return FoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        val food = foodList[position]

        holder.tvFoodName.text = food.name
        holder.tvIngredients.text = "Ingredients: ${food.ingredients}"
        holder.tvMappedAllergens.text = "Mapped Allergens: ${food.allergensMapped}"

        if (food.predictedAllergens.isNotEmpty()) {
            holder.tvPredictedAllergens.text = "Predicted: ${food.predictedAllergens}"
            holder.tvPredictedAllergens.visibility = View.VISIBLE

            // Compare predicted vs mapped allergens
            val predictedNormalized = normalizeAllergenString(food.predictedAllergens)
            val mappedNormalized = normalizeAllergenString(food.allergensMapped)

            // Change color to green if they match, orange if they don't
            if (predictedNormalized == mappedNormalized) {
                holder.tvPredictedAllergens.setTextColor(Color.parseColor("#4CAF50")) // Green
            } else {
                holder.tvPredictedAllergens.setTextColor(Color.parseColor("#FF6B00")) // Orange
            }
        } else {
            holder.tvPredictedAllergens.visibility = View.GONE
        }

        if (food.metrics != null || food.qualityMetrics != null || food.safetyMetrics != null) {
            val sb = StringBuilder()
            val modelDisplayName = food.modelName.substringBefore("-Q").substringBefore(".gguf")
            
            // Model and dataset info
            sb.append("📱 Model: $modelDisplayName | Dataset: ${food.datasetNumber}\n\n")
            
            // Prediction Quality Metrics (Table 2)
            food.qualityMetrics?.let { qm ->
                sb.append("📊 QUALITY METRICS\n")
                sb.append("TP=${qm.truePositives} FP=${qm.falsePositives} ")
                sb.append("FN=${qm.falseNegatives} TN=${qm.trueNegatives}\n")
                sb.append("Precision: ${String.format("%.3f", qm.precision)} | ")
                sb.append("Recall: ${String.format("%.3f", qm.recall)}\n")
                sb.append("F1(micro): ${String.format("%.3f", qm.f1ScoreMicro)} | ")
                sb.append("F1(macro): ${String.format("%.3f", qm.f1ScoreMacro)}\n")
                sb.append("Hamming Loss: ${String.format("%.3f", qm.hammingLoss)} | ")
                sb.append("FNR: ${String.format("%.3f", qm.falseNegativeRate)}\n")
                sb.append("Exact Match: ${if (qm.isExactMatch) "✅" else "❌"}\n\n")
            }
            
            // Safety Metrics (Table 3)
            food.safetyMetrics?.let { sm ->
                sb.append("🔒 SAFETY METRICS\n")
                if (sm.missedAllergens.isNotEmpty()) {
                    sb.append("⚠️ MISSED: ${sm.missedAllergens.joinToString(", ")}\n")
                }
                if (sm.hasOverPrediction) {
                    sb.append("Over-predicted: ${sm.overPredictedAllergens.joinToString(", ")}\n")
                }
                if (sm.hasHallucination) {
                    sb.append("Hallucinated: ${sm.hallucinatedAllergens.joinToString(", ")}\n")
                }
                sm.isCorrectAbstention?.let { correct ->
                    sb.append("Abstention: ${if (correct) "✅ Correct" else "❌ Incorrect"}\n")
                }
                if (!sm.hasOverPrediction && !sm.hasHallucination && sm.missedAllergens.isEmpty()) {
                    sb.append("✅ No safety issues\n")
                }
                sb.append("\n")
            }
            
            // On-Device Efficiency Metrics (Table 4)
            food.metrics?.let { m ->
                sb.append("⚡ EFFICIENCY METRICS\n")
                sb.append("Latency: ${m.latencyMs}ms | TTFT: ${m.ttft}ms\n")
                sb.append("ITPS: ${m.itps} tok/s | OTPS: ${m.otps} tok/s\n")
                sb.append("Memory: Java=${m.javaHeapKb}KB Native=${m.nativeHeapKb}KB PSS=${m.totalPssKb}KB")
            }
            
            holder.tvMetrics.text = sb.toString().trim()
            holder.tvMetrics.visibility = View.VISIBLE
        } else {
            holder.tvMetrics.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = foodList.size

    fun updateFood(position: Int, updatedFood: FoodData) {
        foodList[position] = updatedFood
        notifyItemChanged(position)
    }

    /**
     * Normalize allergen string for comparison
     * Handles different formats, spacing, and ordering
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
}

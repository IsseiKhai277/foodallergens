package edu.utem.ftmk.foodallergen

/**
 * BITP 3453 Mobile Application Development
 * Semester 1, 2025/2026
 *
 * @author Emaliana Kasmuri, FTMK, UTeM
 *
 * Purpose:
 * Data classes for storing all three categories of metrics as per project requirements:
 * 1. Prediction Quality Metrics (Table 2)
 * 2. Safety-Oriented Metrics (Table 3)
 * 3. On-Device Efficiency Metrics (Table 4)
 */

/**
 * Table 2: Prediction Quality Metrics
 * Computed per allergen and aggregated across all test samples
 */
data class PredictionQualityMetrics(
    // Confusion matrix components (per-allergen counts)
    val truePositives: Int = 0,      // TP: Allergen present and correctly predicted
    val falsePositives: Int = 0,     // FP: Allergen not present but incorrectly predicted
    val falseNegatives: Int = 0,     // FN: Allergen present but not predicted (CRITICAL!)
    val trueNegatives: Int = 0,      // TN: Allergen not present and not predicted
    
    // Precision: TP / (TP + FP) - Reliability of positive predictions
    val precision: Double = 0.0,
    
    // Recall: TP / (TP + FN) - Detection rate of actual allergens (SAFETY CRITICAL)
    val recall: Double = 0.0,
    
    // F1 Score: 2TP / (2TP + FP + FN) - Harmonic mean of precision and recall
    val f1ScoreMicro: Double = 0.0,  // Micro F1: Aggregates TP, FP, FN across all allergens
    val f1ScoreMacro: Double = 0.0,  // Macro F1: Averages F1 per allergen
    
    // Exact Match Ratio (EMR): Percentage where prediction exactly matches ground truth
    val isExactMatch: Boolean = false,
    
    // Hamming Loss: (FP + FN) / (N × L) - Fraction of incorrect labels
    val hammingLoss: Double = 0.0,
    
    // False Negative Rate (FNR): FN / (TP + FN) - Proportion of missed allergens
    val falseNegativeRate: Double = 0.0
)

/**
 * Table 3: Safety-Oriented Metrics
 * Assesses reliability and trustworthiness of predictions
 */
data class SafetyMetrics(
    // Hallucination: Allergens predicted that are not in ingredient list
    val hasHallucination: Boolean = false,
    val hallucinatedAllergens: List<String> = emptyList(),
    
    // Over-prediction: Extra allergens beyond ground truth
    val hasOverPrediction: Boolean = false,
    val overPredictedAllergens: List<String> = emptyList(),
    
    // Abstention accuracy: Correctly predicting empty set when no allergens present
    val isCorrectAbstention: Boolean? = null,  // null if not applicable, true/false otherwise
    
    // Missed allergens (for safety analysis)
    val missedAllergens: List<String> = emptyList()
)

/**
 * Aggregated Safety Metrics (for model-level reporting)
 */
data class AggregatedSafetyMetrics(
    val hallucinationRate: Double = 0.0,      // Percentage with hallucinations
    val overPredictionRate: Double = 0.0,     // Percentage with over-predictions
    val abstentionAccuracy: Double = 0.0,     // TNR for empty-label inputs
    val totalSamples: Int = 0,
    val hallucinationCount: Int = 0,
    val overPredictionCount: Int = 0,
    val correctAbstentionCount: Int = 0,
    val abstentionCases: Int = 0
)

/**
 * Aggregated Prediction Quality Metrics (for model-level reporting)
 */
data class AggregatedQualityMetrics(
    val totalTP: Int = 0,
    val totalFP: Int = 0,
    val totalFN: Int = 0,
    val totalTN: Int = 0,
    val avgPrecision: Double = 0.0,
    val avgRecall: Double = 0.0,
    val microF1: Double = 0.0,
    val macroF1: Double = 0.0,
    val exactMatchRatio: Double = 0.0,        // EMR: Percentage of exact matches
    val avgHammingLoss: Double = 0.0,
    val falseNegativeRate: Double = 0.0,
    val totalSamples: Int = 0,
    val exactMatches: Int = 0
)

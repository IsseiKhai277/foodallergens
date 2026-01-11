package edu.utem.ftmk.foodallergen

data class FoodData(
    val id: String,
    val name: String,
    val link: String,
    val ingredients: String,
    val allergens: String,
    val allergensMapped: String,
    var predictedAllergens: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    
    // On-Device Efficiency Metrics (Table 4) - Already implemented
    var metrics: InferenceMetrics? = null,
    
    // Model tracking
    var modelName: String = "",
    var datasetNumber: Int = 0,
    
    // Prediction Quality Metrics (Table 2)
    var qualityMetrics: PredictionQualityMetrics? = null,
    
    // Safety-Oriented Metrics (Table 3)
    var safetyMetrics: SafetyMetrics? = null
)
package edu.utem.ftmk.foodallergen

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * BITP 3453 Mobile Application Development
 *
 *
 *
 * Purpose:
 * Repository for storing food allergen predictions in Firebase Firestore
 */
object FirebaseRepository {

    private val db = FirebaseFirestore.getInstance()
    private const val COLLECTION_NAME = "food_allergen_predictions"

    /**
     * Save food data with prediction results to Firebase
     * Stores only essential prediction records and confusion matrix counts.
     * Metrics are calculated on-the-fly when fetching data.
     */
    suspend fun saveFoodPrediction(food: FoodData): Result<String> {
        return try {
            val data = hashMapOf(
                // Basic prediction record (per project requirements)
                "dataId" to food.id,
                "name" to food.name,
                "ingredients" to food.ingredients,
                "allergensMapped" to food.allergensMapped,
                "predictedAllergens" to food.predictedAllergens,
                "modelName" to food.modelName,
                "datasetNumber" to food.datasetNumber,
                "timestamp" to FieldValue.serverTimestamp(),
                
                // Confusion matrix counts (for metric calculation)
                "truePositives" to (food.qualityMetrics?.truePositives ?: 0),
                "falsePositives" to (food.qualityMetrics?.falsePositives ?: 0),
                "falseNegatives" to (food.qualityMetrics?.falseNegatives ?: 0),
                "trueNegatives" to (food.qualityMetrics?.trueNegatives ?: 0),
                "isExactMatch" to (food.qualityMetrics?.isExactMatch ?: false),
                
                // On-Device Efficiency Metrics (Table 4)
                "latencyMs" to (food.metrics?.latencyMs ?: -1),
                "ttft" to (food.metrics?.ttft ?: -1),
                "itps" to (food.metrics?.itps ?: -1),
                "otps" to (food.metrics?.otps ?: -1),
                "oet" to (food.metrics?.oet ?: -1),
                "javaHeapKb" to (food.metrics?.javaHeapKb ?: -1),
                "nativeHeapKb" to (food.metrics?.nativeHeapKb ?: -1),
                "totalPssKb" to (food.metrics?.totalPssKb ?: -1)
            )

            val documentReference = db.collection(COLLECTION_NAME)
                .add(data)
                .await()

            Result.success(documentReference.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch save multiple food predictions
     */
    suspend fun saveFoodPredictions(foods: List<FoodData>): Result<Int> {
        return try {
            var successCount = 0
            
            foods.forEach { food ->
                val result = saveFoodPrediction(food)
                if (result.isSuccess) {
                    successCount++
                }
            }
            
            Result.success(successCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all predictions for a specific model
     */
    suspend fun getPredictionsByModel(modelName: String): Result<List<FoodData>> {
        return try {
            val snapshot = db.collection(COLLECTION_NAME)
                .whereEqualTo("modelName", modelName)
                .get()
                .await()

            val predictions = snapshot.documents.mapNotNull { doc ->
                documentToFoodData(doc)
            }

            Result.success(predictions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch all predictions grouped by model
     */
    suspend fun getAllPredictionsGroupedByModel(): Result<Map<String, List<FoodData>>> {
        return try {
            val snapshot = db.collection(COLLECTION_NAME)
                .get()
                .await()

            val predictions = snapshot.documents.mapNotNull { doc ->
                documentToFoodData(doc)
            }.groupBy { it.modelName }

            Result.success(predictions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert Firestore document to FoodData
     * Only reconstructs basic data and confusion matrix counts.
     * Full metrics are calculated on-the-fly when needed.
     */
    private fun documentToFoodData(doc: com.google.firebase.firestore.DocumentSnapshot): FoodData? {
        return try {
            FoodData(
                id = doc.getString("dataId") ?: "",
                name = doc.getString("name") ?: "",
                link = "",
                ingredients = doc.getString("ingredients") ?: "",
                allergens = "",
                allergensMapped = doc.getString("allergensMapped") ?: "",
                predictedAllergens = doc.getString("predictedAllergens") ?: "",
                modelName = doc.getString("modelName") ?: "",
                datasetNumber = (doc.getLong("datasetNumber") ?: 0).toInt(),
                timestamp = System.currentTimeMillis(),
                
                // Only store confusion matrix counts (metrics calculated on-the-fly)
                qualityMetrics = PredictionQualityMetrics(
                    truePositives = (doc.getLong("truePositives") ?: 0).toInt(),
                    falsePositives = (doc.getLong("falsePositives") ?: 0).toInt(),
                    falseNegatives = (doc.getLong("falseNegatives") ?: 0).toInt(),
                    trueNegatives = (doc.getLong("trueNegatives") ?: 0).toInt(),
                    isExactMatch = doc.getBoolean("isExactMatch") ?: false
                ),
                
                // Safety metrics calculated on-the-fly from ingredients/predictions
                safetyMetrics = null,
                
                // Reconstruct Inference Metrics
                metrics = InferenceMetrics(
                    latencyMs = doc.getLong("latencyMs") ?: 0,
                    javaHeapKb = doc.getLong("javaHeapKb") ?: 0,
                    nativeHeapKb = doc.getLong("nativeHeapKb") ?: 0,
                    totalPssKb = doc.getLong("totalPssKb") ?: 0,
                    ttft = doc.getLong("ttft") ?: 0,
                    itps = doc.getLong("itps") ?: 0,
                    otps = doc.getLong("otps") ?: 0,
                    oet = doc.getLong("oet") ?: 0
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete all predictions (for testing purposes)
     */
    suspend fun deleteAllPredictions(): Result<Int> {
        return try {
            val snapshot = db.collection(COLLECTION_NAME).get().await()
            var deletedCount = 0
            
            snapshot.documents.forEach { doc ->
                doc.reference.delete().await()
                deletedCount++
            }
            
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

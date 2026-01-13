package edu.utem.ftmk.foodallergen

import android.util.Log
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
    private const val ROOT_COLLECTION = "models"

    /**
     * Get collection name for a specific model
     * Groups predictions by model for better organization
     */
    private fun getModelCollectionPath(modelName: String): String {
        // Sanitize model name for use as collection name
        val sanitizedModelName = modelName.replace(".", "_").replace("-", "_")
        Log.d("FirebaseRepository", "Model path: '$modelName' -> '$sanitizedModelName'")
        return sanitizedModelName
    }

    /**
     * Save food data with prediction results to Firebase
     * Stores predictions in model-specific collections for better organization.
     * Stores only essential prediction records and confusion matrix counts.
     * Metrics are calculated on-the-fly when fetching data.
     */
    suspend fun saveFoodPrediction(food: FoodData): Result<String> {
        return try {
            Log.d("FirebaseRepository", "=== SAVING PREDICTION ===")
            Log.d("FirebaseRepository", "Food: ${food.name}")
            Log.d("FirebaseRepository", "Model: ${food.modelName}")
            Log.d("FirebaseRepository", "Dataset: ${food.datasetNumber}")
            
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

            // Store in model-specific collection: models/{modelName}/{docId}
            val modelCollection = getModelCollectionPath(food.modelName)
            val path = "models/$modelCollection/predictions"
            Log.d("FirebaseRepository", "Saving to path: $path")
            
            // CRITICAL: Create parent model document if it doesn't exist
            // Without this, the parent document won't exist and queries will return empty!
            val modelDocRef = db.collection("models").document(modelCollection)
            modelDocRef.set(hashMapOf(
                "modelName" to food.modelName,
                "lastUpdated" to FieldValue.serverTimestamp()
            ), com.google.firebase.firestore.SetOptions.merge()).await()
            
            val documentReference = modelDocRef
                .collection("predictions")
                .add(data)
                .await()

            Log.d("FirebaseRepository", "✓ Successfully saved with ID: ${documentReference.id}")
            Log.d("FirebaseRepository", "Full path: models/$modelCollection/predictions/${documentReference.id}")
            Result.success(documentReference.id)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "✗ FAILED to save prediction", e)
            Log.e("FirebaseRepository", "Error details: ${e.message}")
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
            val modelCollection = getModelCollectionPath(modelName)
            val snapshot = db.collection("models")
                .document(modelCollection)
                .collection("predictions")
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
     * Uses collectionGroup query to find all predictions across all models
     */
    suspend fun getAllPredictionsGroupedByModel(): Result<Map<String, List<FoodData>>> {
        return try {
            Log.d("FirebaseRepository", "=== FETCHING ALL PREDICTIONS ===")
            Log.d("FirebaseRepository", "Using collectionGroup query for 'predictions'")
            
            // Use collectionGroup to query all 'predictions' subcollections across all models
            val predictionsSnapshot = db.collectionGroup("predictions").get().await()
            Log.d("FirebaseRepository", "Found ${predictionsSnapshot.documents.size} total predictions across all models")
            
            if (predictionsSnapshot.documents.isEmpty()) {
                Log.w("FirebaseRepository", "⚠ No predictions found in any model subcollections")
                Log.w("FirebaseRepository", "This means no predictions have been saved yet")
            }
            
            val allPredictions = mutableMapOf<String, MutableList<FoodData>>()
            
            // Parse all predictions and group by modelName
            predictionsSnapshot.documents.forEach { doc ->
                val foodData = documentToFoodData(doc)
                if (foodData != null) {
                    val modelName = foodData.modelName
                    if (!allPredictions.containsKey(modelName)) {
                        allPredictions[modelName] = mutableListOf()
                        Log.d("FirebaseRepository", "--- Found new model: $modelName ---")
                    }
                    allPredictions[modelName]!!.add(foodData)
                }
            }

            Log.d("FirebaseRepository", "Total models with data: ${allPredictions.size}")
            allPredictions.forEach { (model, preds) ->
                Log.d("FirebaseRepository", "  - $model: ${preds.size} predictions")
            }
            
            Result.success(allPredictions)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error fetching predictions", e)
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
            Log.d("FirebaseRepository", "Parsing document: ${doc.id}")
            
            val foodData = FoodData(
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
            
            Log.d("FirebaseRepository", "Successfully parsed: ${foodData.name}, model: ${foodData.modelName}")
            foodData
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error parsing document ${doc.id}", e)
            null
        }
    }

    /**
     * Delete all predictions (for testing purposes)
     * Deletes all model collections and their predictions
     */
    suspend fun deleteAllPredictions(): Result<Int> {
        return try {
            val modelsSnapshot = db.collection("models").get().await()
            var deletedCount = 0
            
            modelsSnapshot.documents.forEach { modelDoc ->
                // Delete all predictions in this model's subcollection
                val predictionsSnapshot = modelDoc.reference
                    .collection("predictions")
                    .get()
                    .await()
                
                predictionsSnapshot.documents.forEach { predDoc ->
                    predDoc.reference.delete().await()
                    deletedCount++
                }
            }
            
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

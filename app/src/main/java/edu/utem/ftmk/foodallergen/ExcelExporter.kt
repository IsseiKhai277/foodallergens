package edu.utem.ftmk.foodallergen

import android.content.Context
import android.os.Environment
import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * BITP 3453 Mobile Application Development
 *
 * @author Emaliana Kasmuri, FTMK, UTeM
 *
 * Purpose:
 * Export prediction results to Excel file for submission
 * Each model's results are saved in separate tabs
 * Includes all three metric categories as per project requirements
 */
object ExcelExporter {

    /**
     * Export prediction results to Excel file
     * 
     * @param context Application context
     * @param foodDataByModel Map of model name to list of food data
     * @return Result with file path or error
     */
    fun exportToExcel(
        context: Context,
        foodDataByModel: Map<String, List<FoodData>>
    ): Result<String> {
        return try {
            val workbook = XSSFWorkbook()
            
            // Create a sheet for each model
            foodDataByModel.forEach { (modelName, foodList) ->
                createModelSheet(workbook, modelName, foodList)
            }
            
            // Create summary sheet
            createSummarySheet(workbook, foodDataByModel)
            
            // Save file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "FoodAllergen_Predictions_$timestamp.xlsx"
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val file = File(documentsDir, fileName)
            
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
            
            workbook.close()
            
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Log.e("ExcelExporter", "Error exporting to Excel", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create a sheet for a specific model's predictions
     */
    private fun createModelSheet(workbook: XSSFWorkbook, modelName: String, foodList: List<FoodData>) {
        val sheetName = modelName.substringBefore("-Q").substringBefore(".gguf").take(31) // Excel limit
        val sheet = workbook.createSheet(sheetName)
        
        // Header styles
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            val font = workbook.createFont()
            font.bold = true
            setFont(font)
        }
        
        // Create header row - Following project requirements for prediction records
        val headerRow = sheet.createRow(0)
        val headers = listOf(
            "ID",
            "Food Name",
            "Input Ingredients",
            "Ground Truth Allergens",
            "Predicted Allergens",
            "TP",
            "FP",
            "FN",
            "TN",
            "Exact Match"
        )
        
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }
        
        // Data rows - Essential prediction records per project requirements
        foodList.forEachIndexed { rowIndex, food ->
            val row = sheet.createRow(rowIndex + 1)
            var colIndex = 0
            
            // Prediction record: Input ingredients, ground truth, predictions, outcomes
            row.createCell(colIndex++).setCellValue(food.id)
            row.createCell(colIndex++).setCellValue(food.name)
            row.createCell(colIndex++).setCellValue(food.ingredients)
            row.createCell(colIndex++).setCellValue(food.allergensMapped)
            row.createCell(colIndex++).setCellValue(food.predictedAllergens)
            
            // Corresponding outcomes (TP, FP, FN, TN, Exact Match)
            food.qualityMetrics?.let { qm ->
                row.createCell(colIndex++).setCellValue(qm.truePositives.toDouble())
                row.createCell(colIndex++).setCellValue(qm.falsePositives.toDouble())
                row.createCell(colIndex++).setCellValue(qm.falseNegatives.toDouble())
                row.createCell(colIndex++).setCellValue(qm.trueNegatives.toDouble())
                row.createCell(colIndex++).setCellValue(if (qm.isExactMatch) "YES" else "NO")
            } ?: run {
                colIndex += 5 // Skip outcome columns if no metrics
            }
        }
        
        // Note: autoSizeColumn() uses java.awt.font.FontRenderContext which is not available on Android
        // Columns will use default width. Users can manually adjust in Excel if needed.
    }
    
    /**
     * Create summary sheet with aggregated metrics
     */
    private fun createSummarySheet(workbook: XSSFWorkbook, foodDataByModel: Map<String, List<FoodData>>) {
        val sheet = workbook.createSheet("Summary")
        
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            font.color = IndexedColors.WHITE.index
            setFont(font)
        }
        
        var rowIndex = 0
        
        // Title
        val titleRow = sheet.createRow(rowIndex++)
        titleRow.createCell(0).setCellValue("Food Allergen Prediction - Model Comparison Summary")
        
        rowIndex++ // Empty row
        
        foodDataByModel.forEach { (modelName, foodList) ->
            // Model name
            val modelRow = sheet.createRow(rowIndex++)
            modelRow.createCell(0).apply {
                setCellValue("Model: ${modelName.substringBefore("-Q")}")
                cellStyle = headerStyle
            }
            
            // Calculate aggregated metrics here (simplified for now)
            val qualityRow = sheet.createRow(rowIndex++)
            qualityRow.createCell(0).setCellValue("Total Samples:")
            qualityRow.createCell(1).setCellValue(foodList.size.toDouble())
            
            val exactMatches = foodList.count { it.qualityMetrics?.isExactMatch == true }
            val emrRow = sheet.createRow(rowIndex++)
            emrRow.createCell(0).setCellValue("Exact Match Ratio (EMR):")
            emrRow.createCell(1).setCellValue("${String.format("%.2f", (exactMatches.toDouble() / foodList.size) * 100)}%")
            
            rowIndex++ // Empty row
        }
        
        // Auto-size
        sheet.autoSizeColumn(0)
        sheet.autoSizeColumn(1)
    }
}

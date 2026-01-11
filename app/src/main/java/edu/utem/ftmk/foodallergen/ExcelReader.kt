package edu.utem.ftmk.foodallergen

import android.content.Context
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

/**
 * BITP 3453 Mobile Application Development
 *
 *
 *
 * Purpose:
 * Utility class to read food data from Excel file
 */
object ExcelReader {

    fun readFoodData(context: Context): List<FoodData> {
        val foodList = mutableListOf<FoodData>()
        
        try {
            val inputStream: InputStream = context.assets.open("foodpreprocessed.xlsx")
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            
            // Skip header row (row 0)
            for (i in 1 until sheet.physicalNumberOfRows) {
                val row = sheet.getRow(i) ?: continue
                
                try {
                    val id = row.getCell(0)?.toString() ?: ""
                    val name = row.getCell(1)?.toString() ?: ""
                    val link = row.getCell(2)?.toString() ?: ""
                    val ingredients = row.getCell(3)?.toString() ?: ""
                    val allergens = row.getCell(4)?.toString() ?: ""
                    val allergensMapped = row.getCell(5)?.toString() ?: ""
                    
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        foodList.add(
                            FoodData(
                                id = id,
                                name = name,
                                link = link,
                                ingredients = ingredients,
                                allergens = allergens,
                                allergensMapped = allergensMapped
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip problematic rows
                    continue
                }
            }
            
            workbook.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return foodList
    }
    
    /**
     * Divide the food list into 20 equal sets
     */
    fun divideIntoSets(foodList: List<FoodData>, numberOfSets: Int = 20): List<List<FoodData>> {
        if (foodList.isEmpty()) return emptyList()
        
        val setSize = foodList.size / numberOfSets
        val remainder = foodList.size % numberOfSets
        
        val sets = mutableListOf<List<FoodData>>()
        var currentIndex = 0
        
        for (i in 0 until numberOfSets) {
            val currentSetSize = if (i < remainder) setSize + 1 else setSize
            val endIndex = minOf(currentIndex + currentSetSize, foodList.size)
            
            if (currentIndex < foodList.size) {
                sets.add(foodList.subList(currentIndex, endIndex))
                currentIndex = endIndex
            }
        }
        
        return sets
    }
}

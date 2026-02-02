package com.example.chat_ui.data

import android.content.Context
import org.json.JSONObject

data class CatalogModel(
    val id: String,
    val name: String,
    val company: String,
    val features: String,
    val vision: Boolean,
    val thinking: Boolean,
    val tools: Boolean
)

object ModelsCatalogLoader {
    private var modelsMap: Map<String, CatalogModel>? = null
    private var modelsList: List<CatalogModel>? = null
    
    fun loadCatalog(context: Context) {
        if (modelsMap != null) return
        
        try {
            val jsonString = context.assets.open("models_catalog.json")
                .bufferedReader()
                .use { it.readText() }
            
            val json = JSONObject(jsonString)
            val modelsArray = json.getJSONArray("models")
            val models = mutableListOf<CatalogModel>()
            
            for (i in 0 until modelsArray.length()) {
                val modelJson = modelsArray.getJSONObject(i)
                models.add(CatalogModel(
                    id = modelJson.optString("id", ""),
                    name = modelJson.optString("name", ""),
                    company = modelJson.optString("company", ""),
                    features = modelJson.optString("features", ""),
                    vision = modelJson.optBoolean("vision", false),
                    thinking = modelJson.optBoolean("thinking", false),
                    tools = modelJson.optBoolean("tools", false)
                ))
            }
            
            modelsList = models
            modelsMap = models.associateBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            modelsList = emptyList()
            modelsMap = emptyMap()
        }
    }
    
    fun getModelInfo(context: Context, modelId: String): CatalogModel? {
        if (modelsMap == null) loadCatalog(context)
        return modelsMap?.get(modelId)
    }
    
    fun getAllModels(context: Context): List<CatalogModel> {
        if (modelsList == null) loadCatalog(context)
        return modelsList ?: emptyList()
    }
}

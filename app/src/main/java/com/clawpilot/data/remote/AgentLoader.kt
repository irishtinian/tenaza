package com.clawpilot.data.remote

import android.util.Log
import com.clawpilot.ui.dashboard.AgentInfo
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "AgentLoader"

/**
 * Carga la lista completa de agentes con nombre, emoji y modelo.
 * Usa config.get (info completa) con fallback a agents.list (solo id+name).
 */
suspend fun loadAgentsFromGateway(rpcClient: GatewayRpcClient): List<AgentInfo> {
    try {
        // config.get {} devuelve toda la config incluyendo agentes con modelo/emoji
        val response = rpcClient.request("config.get")
        val agentsArray = response.payload?.jsonObject
            ?.get("config")?.jsonObject
            ?.get("agents")?.jsonObject
            ?.get("list")?.jsonArray

        if (agentsArray != null) {
            return agentsArray.map { element ->
                val obj = element.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content
                    ?: obj["displayName"]?.jsonPrimitive?.content
                    ?: obj["id"]?.jsonPrimitive?.content
                    ?: "?"
                val emoji = obj["emoji"]?.jsonPrimitive?.content ?: ""
                val model = obj["model"]?.jsonObject
                    ?.get("primary")?.jsonPrimitive?.content ?: ""
                AgentInfo(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    displayName = name,
                    emoji = emoji,
                    model = model.substringAfterLast("/")
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "config.get failed: ${e.message}")
    }

    // Fallback a agents.list (solo id + name)
    try {
        val fallback = rpcClient.request("agents.list")
        if (fallback.ok) {
            val arr = fallback.payload?.jsonObject?.get("agents")?.jsonArray ?: return emptyList()
            return arr.map { element ->
                val obj = element.jsonObject
                AgentInfo(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    displayName = obj["name"]?.jsonPrimitive?.content
                        ?: obj["id"]?.jsonPrimitive?.content ?: "?",
                    emoji = "",
                    model = ""
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "agents.list failed: ${e.message}")
    }

    return emptyList()
}

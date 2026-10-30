package com.abhishek.antiscroll.data

import android.content.res.AssetManager
import com.abhishek.antiscroll.rules.profile.InstagramRules
import com.abhishek.antiscroll.rules.profile.Surface
import org.json.JSONArray
import org.json.JSONObject

/** Reads instagram_rules.json from assets. A broken file fails loudly at startup. */
class InstagramRulesLoader(private val assets: AssetManager) {

    fun load(): InstagramRules = parse(assets.open(FILE_NAME).bufferedReader().use { it.readText() })

    companion object {
        const val FILE_NAME = "instagram_rules.json"

        fun parse(json: String): InstagramRules {
            val root = JSONObject(json)
            val tabs = root.getJSONArray("tabs")
            return InstagramRules(
                tabs = (0 until tabs.length()).map { i ->
                    val tab = tabs.getJSONObject(i)
                    InstagramRules.Tab(
                        id = tab.getString("id"),
                        label = tab.getString("label"),
                        surface = Surface.valueOf(tab.getString("surface")),
                    )
                },
                tabPagerId = root.getString("tabPagerId"),
                reelsPagerId = root.getString("reelsPagerId"),
                photoPostInReelsViewerIds = root.optJSONArray("photoPostInReelsViewerIds")?.toStrings().orEmpty(),
                dmThreadIds = root.getJSONArray("dmThreadIds").toStrings(),
                storyViewerIds = root.getJSONArray("storyViewerIds").toStrings(),
            )
        }

        private fun JSONArray.toStrings(): List<String> = (0 until length()).map { getString(it) }
    }
}

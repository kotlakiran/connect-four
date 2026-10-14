package com.kirankotla.connectfour

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

const val MORE_GAMES_MANIFEST_URL = "https://raw.githubusercontent.com/kotlakiran/more-games/main/apps.json"
private const val ICON_BASE = "https://raw.githubusercontent.com/kotlakiran/more-games/main/icons/"
private fun iconUrl(pkg: String) = "$ICON_BASE$pkg.png"

data class GameApp(val name: String, val packageId: String, val tagline: String, val published: Boolean, val accent: Int, val icon: String = "")

val moreGamesDefaults = listOf(
    GameApp("Connect Four", "com.kirankotla.connectfour", "4-in-a-row strategy", true, 0xFF2CD7DC.toInt(), iconUrl("com.kirankotla.connectfour")),
    GameApp("Box 3D Arena", "com.kotlakiran.box3d", "3D Spatial Strategy", false, 0xFF82E0B1.toInt(), iconUrl("com.kotlakiran.box3d")),
    GameApp("Tally Haptic", "com.kirankotla.tallyhaptic", "Haptic habit counter", true, 0xFFEEA85C.toInt(), iconUrl("com.kirankotla.tallyhaptic")),
    GameApp("Arrow Flow", "com.game.arrowflow", "Arrow-matching puzzle", true, 0xFFFF7060.toInt(), iconUrl("com.game.arrowflow")),
    GameApp("Rangoli Flow", "com.kirankotla.rangoliflow", "Conveyor-sort rangoli art", false, 0xFFEEA85C.toInt(), iconUrl("com.kirankotla.rangoliflow")),
    GameApp("Rescue Fireflies", "com.kirankotla.rescuefireflies", "Light-up rescue puzzle", false, 0xFF2CD7DC.toInt(), iconUrl("com.kirankotla.rescuefireflies")),
    GameApp("Crown Grid", "com.kirankotla.crowngrid.queens", "Queens logic puzzle", false, 0xFF82E0B1.toInt(), iconUrl("com.kirankotla.crowngrid.queens")),
    GameApp("CartNote AI", "com.kirankotla.cartnote", "Smart grocery lists", false, 0xFFFF7060.toInt(), iconUrl("com.kirankotla.cartnote")),
    GameApp("Crop Season", "com.kirankotla.cropseason", "Farm season P&L ledger", false, 0xFFEEA85C.toInt(), iconUrl("com.kirankotla.cropseason")),
    GameApp("Whooshy", "com.kirankotla.arrowflow.ultimate", "Slide-the-arrows puzzle", true, 0xFF2CD7DC.toInt(), iconUrl("com.kirankotla.arrowflow.ultimate")),
    GameApp("Sum Foundry", "com.kirankotla.sumfoundry", "Number logic puzzle", false, 0xFF82E0B1.toInt(), iconUrl("com.kirankotla.sumfoundry"))
)

class MoreGamesCatalog(private val context: Context, private val manifestUrl: String = MORE_GAMES_MANIFEST_URL) {
    @Volatile var apps: List<GameApp> = moreGamesDefaults; private set
    private val icons = ConcurrentHashMap<String, Bitmap>()
    private val tried = Collections.synchronizedSet(HashSet<String>())
    private val handler = Handler(Looper.getMainLooper())
    fun iconFor(packageId: String): Bitmap? = icons[packageId]
    fun refresh(onChanged: () -> Unit) {
        loadIcons(onChanged)
        Thread {
            val parsed = runCatching {
                val c = (URL(manifestUrl).openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 6000 }
                val text = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect(); parse(text)
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) handler.post { apps = parsed; tried.clear(); onChanged(); loadIcons(onChanged) }
        }.apply { isDaemon = true }.start()
    }
    private fun loadIcons(onChanged: () -> Unit) {
        apps.forEach { app ->
            if (!tried.add(app.packageId)) return@forEach
            Thread {
                val bitmap = runCatching { drawableToBitmap(context.packageManager.getApplicationIcon(app.packageId), 144) }.getOrNull()
                    ?: runCatching {
                        if (app.icon.isBlank()) null else {
                            val c = (URL(app.icon).openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 6000 }
                            val b = BitmapFactory.decodeStream(c.inputStream); c.disconnect(); b
                        }
                    }.getOrNull()
                if (bitmap != null) { icons[app.packageId] = bitmap; handler.post(onChanged) }
            }.start()
        }
    }
    private fun drawableToBitmap(d: Drawable, size: Int): Bitmap { if (d is BitmapDrawable) return d.bitmap; val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, size, size); d.draw(Canvas(b)); return b }
    private fun parse(json: String): List<GameApp> {
        val a = runCatching { JSONObject(json).optJSONArray("apps") }.getOrNull() ?: return emptyList()
        val colors = listOf(0xFF2CD7DC.toInt(), 0xFF82E0B1.toInt(), 0xFFEEA85C.toInt(), 0xFFFF7060.toInt())
        return (0 until a.length()).mapNotNull { i -> val o = a.optJSONObject(i) ?: return@mapNotNull null; val n = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null; val p = o.optString("package").takeIf { it.isNotBlank() } ?: return@mapNotNull null; GameApp(n, p, o.optString("tagline"), o.optBoolean("published"), runCatching { Color.parseColor(o.optString("accent")) }.getOrDefault(colors[i % colors.size]), o.optString("icon")) }
    }
}

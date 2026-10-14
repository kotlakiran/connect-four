package com.kirankotla.connectfour.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.GameApp
import com.kirankotla.connectfour.MoreGamesCatalog

@Composable
fun MoreGamesScreen(selfPackage: String = "", onBack: () -> Unit) {
    val context = LocalContext.current
    val catalog = remember { MoreGamesCatalog(context) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { catalog.refresh { refresh++ } }
    @Suppress("UNUSED_EXPRESSION") refresh
    val apps = catalog.apps.filter { it.packageId != selfPackage }

    fun open(app: GameApp) {
        if (!app.published) return
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageId}"))
        val opened = runCatching { context.startActivity(market) }.isSuccess
        if (!opened) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageId}"))) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF12161B), Color(0xFF0A0D11))))
            .padding(horizontal = 16.dp)
    ) {
        Text("MORE GAMES", color = Color(0xFFEBF2F5), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 24.dp))
        Text("From the same studio", color = Color(0xFFEEA85C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps) { app -> AppRow(app, catalog) { open(app) } }
        }
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEA85C)), modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Text("BACK", color = Color(0xFF0A0D11), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun AppRow(app: GameApp, catalog: MoreGamesCatalog, onOpen: () -> Unit) {
    val icon = catalog.iconFor(app.packageId)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF161D23)).clickable { onOpen() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Image(icon.asImageBitmap(), app.name, Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)))
        else Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color(app.accent)), contentAlignment = Alignment.Center) { Text(app.name.take(1), fontWeight = FontWeight.ExtraBold) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(app.name, color = Color(0xFFEBF2F5), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.tagline, color = Color(0xFFB4C1C9), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (app.published) Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEA85C)), shape = RoundedCornerShape(10.dp)) {
            Text("PLAY", color = Color(0xFF0A0D11), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        } else Text("SOON", color = Color(0xFF2CD7DC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

package com.epos.waiter.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF0B6E99)
val FreeGreen = Color(0xFF2E9D50)
val OrderedBlue = Color(0xFF1E63C6)
val BilledRed = Color(0xFFC7372F)
val SentGrey = Color(0xFF6B7280)

private val colors: ColorScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Color(0xFFE07A1F),
    onSecondary = Color.White,
    primaryContainer = Color(0xFFD5EEF8),
    onPrimaryContainer = Color(0xFF033A52),
    secondaryContainer = Color(0xFFFCE6D2),
    onSecondaryContainer = Color(0xFF5A2E06),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
)

@Composable
fun EposTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

/** True on tablets / wide screens: show menu and order side by side. */
@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 720

/** Images arrive as Base64 text (same as the old PHP). Empty or broken images give null. */
@Composable
fun rememberBase64Image(data: String?): ImageBitmap? = remember(data) {
    if (data.isNullOrBlank()) null
    else try {
        val bytes = Base64.decode(data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/** Round photo, or the first letter of the name when there is no photo. */
@Composable
fun Avatar(image: String?, name: String, size: Dp = 56.dp) {
    val bmp = rememberBase64Image(image)
    if (bmp != null) {
        Image(bmp, contentDescription = name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), fontSize = (size.value / 2.4f).sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

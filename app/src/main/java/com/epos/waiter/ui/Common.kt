package com.epos.waiter.ui

import com.epos.waiter.R

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ePOS brand colours (Ticket Fork logo). */
val Navy = Color(0xFF14213D)
val Saffron = Color(0xFFF59E0B)
val Brand = Navy
val FreeGreen = Color(0xFF2E9D50)
val OrderedBlue = Color(0xFF1E63C6)
val BilledRed = Color(0xFFC7372F)
val SentGrey = Color(0xFF6B7280)

private val colors: ColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Saffron,
    onSecondary = Navy,
    primaryContainer = Color(0xFFDDE3F0),
    onPrimaryContainer = Navy,
    secondaryContainer = Color(0xFFFDEBC8),
    onSecondaryContainer = Color(0xFF4A2F00),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
)

/** Plus Jakarta Sans (bundled, works offline). One variable font file covers every weight. */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    R.font.plus_jakarta_sans, weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Jakarta = FontFamily(
    jakarta(FontWeight.Normal), jakarta(FontWeight.Medium), jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold), jakarta(FontWeight.ExtraBold),
)

private val typography: Typography = Typography().let { t ->
    fun TextStyle.j() = copy(fontFamily = Jakarta)
    Typography(
        displayLarge = t.displayLarge.j(), displayMedium = t.displayMedium.j(), displaySmall = t.displaySmall.j(),
        headlineLarge = t.headlineLarge.j(), headlineMedium = t.headlineMedium.j(), headlineSmall = t.headlineSmall.j(),
        titleLarge = t.titleLarge.j(), titleMedium = t.titleMedium.j(), titleSmall = t.titleSmall.j(),
        bodyLarge = t.bodyLarge.j(), bodyMedium = t.bodyMedium.j(), bodySmall = t.bodySmall.j(),
        labelLarge = t.labelLarge.j(), labelMedium = t.labelMedium.j(), labelSmall = t.labelSmall.j(),
    )
}

@Composable
fun EposTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
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

package app.elduro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Verde = Color(0xFF1B5E3F)
private val VerdeClaro = Color(0xFF7FD3A6)
private val Oro = Color(0xFFB8860B)
private val OroClaro = Color(0xFFF2C14E)

private val Claro = lightColorScheme(
    primary = Verde, onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFD9), onPrimaryContainer = Color(0xFF00210F),
    secondary = Oro, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8B3), onSecondaryContainer = Color(0xFF2B1D00),
    background = Color(0xFFF7F8F4), surface = Color(0xFFF7F8F4),
    surfaceVariant = Color(0xFFE2E8DF), error = Color(0xFFB3261E),
)

private val Oscuro = darkColorScheme(
    primary = VerdeClaro, onPrimary = Color(0xFF00391E),
    primaryContainer = Color(0xFF0F4A2E), onPrimaryContainer = Color(0xFFCDEFD9),
    secondary = OroClaro, onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5B4300), onSecondaryContainer = Color(0xFFFFE8B3),
    background = Color(0xFF101411), surface = Color(0xFF101411),
    surfaceVariant = Color(0xFF2A322C), error = Color(0xFFF2B8B5),
)

/** Colores semánticos para montos. */
object Colores {
    @Composable fun entrada() = if (isSystemInDarkTheme()) Color(0xFF7FD3A6) else Color(0xFF1E7D4A)
    @Composable fun gasto() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)
    @Composable fun neutro() = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun aviso() = if (isSystemInDarkTheme()) Color(0xFFF2C14E) else Color(0xFF9A6B00)
}

@Composable
fun TemaElDuro(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = content)
}

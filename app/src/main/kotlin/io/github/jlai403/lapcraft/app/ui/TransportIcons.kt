package io.github.jlai403.lapcraft.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Material Symbols paths kept local so the debug APK avoids the icons-extended artifact. */
internal object TransportIcons {
    val Pause: ImageVector = icon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    val Rewind: ImageVector = icon("Rewind", "M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z")
    val Forward: ImageVector = icon("Forward", "M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z")
    val SkipPrevious: ImageVector = icon("SkipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z")
    val SkipNext: ImageVector = icon("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6z")

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        ).build()
}

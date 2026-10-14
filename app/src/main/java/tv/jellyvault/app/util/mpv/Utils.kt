package tv.jellyvault.app.util.mpv

import androidx.compose.ui.graphics.Color
import tv.jellyvault.app.mpv.MPVLib

fun MPVLib.setPropertyColor(
    property: String,
    color: Color,
) = setPropertyString(property, color.mpvFormat)

private val Color.mpvFormat: String get() = "$red/$green/$blue/$alpha"

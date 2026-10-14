package tv.jellyvault.app.ui.preferences.tmdb

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * The dark modules of a QR code for [text], row by row, without a quiet zone
 */
fun qrModules(text: String): List<List<Boolean>> {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    return (0 until matrix.height).map { y -> (0 until matrix.width).map { x -> matrix[x, y] } }
}

/**
 * A QR code drawn black on white (phones read it on any theme), with its quiet zone
 */
@Composable
fun QrCode(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val modules = remember(text) { qrModules(text) }
    Canvas(modifier = modifier.background(Color.White).padding(12.dp).size(size)) {
        val cell = this.size.width / modules.size
        modules.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                if (dark) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

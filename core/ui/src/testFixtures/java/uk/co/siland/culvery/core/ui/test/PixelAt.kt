package uk.co.siland.culvery.core.ui.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Dp

/** The window [node] sits in, drawn to a bitmap (`captureToImage` times out under Robolectric); needs `GraphicsMode.NATIVE`. */
fun windowBitmap(node: SemanticsNodeInteraction): Bitmap {
    val window = (node.fetchSemanticsNode().root as View).rootView
    val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
    window.draw(Canvas(bitmap))
    return bitmap
}

/** The window pixel [dx] right and [dy] down from the node's top-left corner, as ARGB; needs `GraphicsMode.NATIVE`. */
fun pixelAt(node: SemanticsNodeInteraction, dx: Dp, dy: Dp): Int {
    val semantics = node.fetchSemanticsNode()
    val bitmap = windowBitmap(node)
    val density = semantics.layoutInfo.density.density
    val bounds = semantics.boundsInWindow
    return bitmap.getPixel((bounds.left + dx.value * density).toInt(), (bounds.top + dy.value * density).toInt())
}

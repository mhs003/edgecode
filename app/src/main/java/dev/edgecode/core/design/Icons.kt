package dev.edgecode.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icon language.
 *
 * Hand-authored rather than pulled from an icon pack: a single 24dp grid, a
 * single 1.8dp stroke, round caps and joins, and no filled glyphs except where
 * weight is needed (status, model, agent). That consistency is what stops the
 * interface reading as assembled from parts — and it keeps ~35k generated icon
 * classes out of the APK, which matters when the build runs on the phone.
 *
 * Every icon is tinted by [androidx.compose.material3.Icon]; colour here is
 * only a placeholder that the tint replaces.
 */
object EdgeIcons {

    private const val W = 1.8f

    private fun strokeIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    /** Outlined glyph drawn with the shared stroke weight. */
    private fun ImageVector.Builder.stroke(block: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = W,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )

    /** Heavier stroke, for small secondary glyphs. */
    private fun ImageVector.Builder.strokeLight(block: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )

    private fun ImageVector.Builder.solid(block: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), pathBuilder = block)

    /** A dot, drawn as a zero-length round-capped line. */
    private fun PathBuilder.dot(x: Float, y: Float) {
        moveTo(x, y)
        lineTo(x, y + 0.01f)
    }

    /** Filled circle, for knobs and other solid detail. */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    val Back: ImageVector by lazy {
        strokeIcon("Back") {
            stroke {
                moveTo(19f, 12f); lineTo(5.5f, 12f)
                moveTo(11.5f, 6f); lineTo(5.5f, 12f); lineTo(11.5f, 18f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight") {
            stroke { moveTo(9.5f, 5f); lineTo(16.5f, 12f); lineTo(9.5f, 19f) }
        }
    }

    val ChevronDown: ImageVector by lazy {
        strokeIcon("ChevronDown") {
            stroke { moveTo(5f, 9.5f); lineTo(12f, 16.5f); lineTo(19f, 9.5f) }
        }
    }

    val ChevronUp: ImageVector by lazy {
        strokeIcon("ChevronUp") {
            stroke { moveTo(5f, 14.5f); lineTo(12f, 7.5f); lineTo(19f, 14.5f) }
        }
    }

    val Close: ImageVector by lazy {
        strokeIcon("Close") {
            stroke {
                moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
            }
        }
    }

    val Check: ImageVector by lazy {
        strokeIcon("Check") {
            stroke { moveTo(5f, 12.8f); lineTo(9.8f, 17.5f); lineTo(19f, 6.8f) }
        }
    }

    val Plus: ImageVector by lazy {
        strokeIcon("Plus") {
            stroke {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }
    }

    val More: ImageVector by lazy {
        strokeIcon("More") {
            stroke {
                dot(12f, 5.5f)
                dot(12f, 12f)
                dot(12f, 18.5f)
            }
        }
    }

    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            stroke {
                moveTo(5f, 11f)
                arcTo(6f, 6f, 0f, true, true, 17f, 11f)
                arcTo(6f, 6f, 0f, true, true, 5f, 11f)
                close()
                moveTo(15.4f, 15.4f); lineTo(19.8f, 19.8f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        strokeIcon("Refresh") {
            stroke {
                moveTo(4.5f, 12f)
                arcTo(7.5f, 7.5f, 0f, true, true, 19.5f, 12f)
                moveTo(16.2f, 8.8f); lineTo(19.5f, 12f); lineTo(16.2f, 15.2f)
            }
        }
    }

    /**
     * Settings. Sliders rather than a gear: legible at 20dp, and the tracks are
     * broken around each knob so it reads as a control rather than as a menu.
     */
    val Settings: ImageVector by lazy {
        strokeIcon("Settings") {
            stroke {
                moveTo(4f, 9f); lineTo(6.6f, 9f)
                moveTo(12.4f, 9f); lineTo(20f, 9f)
                moveTo(4f, 15f); lineTo(12.1f, 15f)
                moveTo(17.9f, 15f); lineTo(20f, 15f)
            }
            solid {
                circle(9.5f, 9f, 2.9f)
                circle(15f, 15f, 2.9f)
            }
        }
    }

    val Send: ImageVector by lazy {
        strokeIcon("Send") {
            stroke {
                moveTo(12f, 19f); lineTo(12f, 5.5f)
                moveTo(6.2f, 11.2f); lineTo(12f, 5.5f); lineTo(17.8f, 11.2f)
            }
        }
    }

    val Stop: ImageVector by lazy {
        strokeIcon("Stop") {
            solid {
                moveTo(9f, 7f); lineTo(15f, 7f)
                quadTo(17f, 7f, 17f, 9f); lineTo(17f, 15f)
                quadTo(17f, 17f, 15f, 17f); lineTo(9f, 17f)
                quadTo(7f, 17f, 7f, 15f); lineTo(7f, 9f)
                quadTo(7f, 7f, 9f, 7f)
                close()
            }
        }
    }

    val Attach: ImageVector by lazy {
        strokeIcon("Attach") {
            stroke {
                moveTo(8.5f, 4.5f); lineTo(15.5f, 4.5f)
                quadTo(19.5f, 4.5f, 19.5f, 8.5f); lineTo(19.5f, 15.5f)
                quadTo(19.5f, 19.5f, 15.5f, 19.5f); lineTo(8.5f, 19.5f)
                quadTo(4.5f, 19.5f, 4.5f, 15.5f); lineTo(4.5f, 8.5f)
                quadTo(4.5f, 4.5f, 8.5f, 4.5f)
                close()
                moveTo(12f, 9f); lineTo(12f, 15f)
                moveTo(9f, 12f); lineTo(15f, 12f)
            }
        }
    }

    val Terminal: ImageVector by lazy {
        strokeIcon("Terminal") {
            stroke {
                moveTo(6.5f, 5f); lineTo(17.5f, 5f)
                quadTo(20f, 5f, 20f, 7.5f); lineTo(20f, 16.5f)
                quadTo(20f, 19f, 17.5f, 19f); lineTo(6.5f, 19f)
                quadTo(4f, 19f, 4f, 16.5f); lineTo(4f, 7.5f)
                quadTo(4f, 5f, 6.5f, 5f)
                close()
                moveTo(7.8f, 10f); lineTo(10.4f, 12.4f); lineTo(7.8f, 14.8f)
                moveTo(13f, 14.8f); lineTo(16.4f, 14.8f)
            }
        }
    }

    val File: ImageVector by lazy {
        strokeIcon("File") {
            stroke {
                moveTo(6.5f, 3.5f); lineTo(13.8f, 3.5f); lineTo(19f, 8.7f); lineTo(19f, 20.5f)
                lineTo(6.5f, 20.5f); close()
                moveTo(13.8f, 3.5f); lineTo(13.8f, 8.7f); lineTo(19f, 8.7f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        strokeIcon("Copy") {
            stroke {
                moveTo(8.5f, 4.5f); lineTo(14f, 4.5f)
                quadTo(16f, 4.5f, 16f, 6.5f); lineTo(16f, 12f)
                moveTo(9f, 8.5f); lineTo(15.5f, 8.5f)
                quadTo(19.5f, 8.5f, 19.5f, 12.5f); lineTo(19.5f, 17.5f)
                quadTo(19.5f, 19.5f, 17.5f, 19.5f); lineTo(11f, 19.5f)
                quadTo(8.5f, 19.5f, 8.5f, 17f); close()
            }
        }
    }

    val Folder: ImageVector by lazy {
        strokeIcon("Folder") {
            stroke {
                moveTo(3.5f, 7.5f); lineTo(3.5f, 17f)
                quadTo(3.5f, 18.5f, 5f, 18.5f); lineTo(19f, 18.5f)
                quadTo(20.5f, 18.5f, 20.5f, 17f); lineTo(20.5f, 9.5f)
                quadTo(20.5f, 8f, 19f, 8f); lineTo(12f, 8f)
                lineTo(10f, 5.5f); lineTo(5f, 5.5f)
                quadTo(3.5f, 5.5f, 3.5f, 7.5f)
                close()
            }
        }
    }

    /** Model. A bolt — reads as capability/energy, not as a brand glyph. */
    val Bolt: ImageVector by lazy {
        strokeIcon("Bolt") {
            solid {
                moveTo(13.6f, 2.5f); lineTo(4.6f, 13.6f); lineTo(10.9f, 13.6f)
                lineTo(10.3f, 21.5f); lineTo(19.4f, 10.4f); lineTo(13.1f, 10.4f)
                close()
            }
        }
    }

    /** Agent. A four-point spark. */
    val Sparkle: ImageVector by lazy {
        strokeIcon("Sparkle") {
            solid {
                moveTo(12f, 3f)
                quadTo(13.2f, 10.8f, 21f, 12f)
                quadTo(13.2f, 13.2f, 12f, 21f)
                quadTo(10.8f, 13.2f, 3f, 12f)
                quadTo(10.8f, 10.8f, 12f, 3f)
                close()
            }
        }
    }

    val Sun: ImageVector by lazy {
        strokeIcon("Sun") {
            stroke {
                moveTo(8f, 12f)
                arcTo(4f, 4f, 0f, true, true, 16f, 12f)
                arcTo(4f, 4f, 0f, true, true, 8f, 12f)
                close()
                moveTo(12f, 2.5f); lineTo(12f, 4.8f)
                moveTo(12f, 19.2f); lineTo(12f, 21.5f)
                moveTo(2.5f, 12f); lineTo(4.8f, 12f)
                moveTo(19.2f, 12f); lineTo(21.5f, 12f)
                moveTo(5.3f, 5.3f); lineTo(6.9f, 6.9f)
                moveTo(17.1f, 17.1f); lineTo(18.7f, 18.7f)
                moveTo(18.7f, 5.3f); lineTo(17.1f, 6.9f)
                moveTo(6.9f, 17.1f); lineTo(5.3f, 18.7f)
            }
        }
    }

    val Moon: ImageVector by lazy {
        strokeIcon("Moon") {
            solid {
                moveTo(20.5f, 14.6f)
                arcTo(8.6f, 8.6f, 0f, false, true, 9.4f, 3.5f)
                arcTo(7.6f, 7.6f, 0f, false, false, 20.5f, 14.6f)
                close()
            }
        }
    }

    /** Server. Two stacked units — the host the app is talking to. */
    val Server: ImageVector by lazy {
        strokeIcon("Server") {
            stroke {
                moveTo(6f, 4.5f); lineTo(18f, 4.5f)
                quadTo(19.5f, 4.5f, 19.5f, 6f); lineTo(19.5f, 8.5f)
                quadTo(19.5f, 10f, 18f, 10f); lineTo(6f, 10f)
                quadTo(4.5f, 10f, 4.5f, 8.5f); lineTo(4.5f, 6f)
                quadTo(4.5f, 4.5f, 6f, 4.5f)
                close()
                moveTo(6f, 14f); lineTo(18f, 14f)
                quadTo(19.5f, 14f, 19.5f, 15.5f); lineTo(19.5f, 18f)
                quadTo(19.5f, 19.5f, 18f, 19.5f); lineTo(6f, 19.5f)
                quadTo(4.5f, 19.5f, 4.5f, 18f); lineTo(4.5f, 15.5f)
                quadTo(4.5f, 14f, 6f, 14f)
                close()
            }
            solid { dot(8f, 7.2f); dot(8f, 16.8f) }
        }
    }

    val Warning: ImageVector by lazy {
        strokeIcon("Warning") {
            stroke {
                moveTo(12f, 3.8f); lineTo(21f, 19.4f); lineTo(3f, 19.4f); close()
                moveTo(12f, 10f); lineTo(12f, 14.2f)
            }
            solid { dot(12f, 16.8f) }
        }
    }

    val Info: ImageVector by lazy {
        strokeIcon("Info") {
            stroke {
                moveTo(3.5f, 12f)
                arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12f)
                arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12f)
                close()
                moveTo(12f, 11f); lineTo(12f, 16.8f)
            }
            solid { dot(12f, 7.9f) }
        }
    }

    val Person: ImageVector by lazy {
        strokeIcon("Person") {
            stroke {
                moveTo(8.4f, 9f)
                arcTo(3.6f, 3.6f, 0f, true, true, 15.6f, 9f)
                arcTo(3.6f, 3.6f, 0f, true, true, 8.4f, 9f)
                close()
                moveTo(4.8f, 20f)
                arcTo(7.2f, 7.2f, 0f, false, true, 19.2f, 20f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        strokeIcon("Lock") {
            stroke {
                moveTo(6.5f, 10.5f); lineTo(17.5f, 10.5f)
                quadTo(19f, 10.5f, 19f, 12f); lineTo(19f, 18f)
                quadTo(19f, 19.5f, 17.5f, 19.5f); lineTo(6.5f, 19.5f)
                quadTo(5f, 19.5f, 5f, 18f); lineTo(5f, 12f)
                quadTo(5f, 10.5f, 6.5f, 10.5f)
                close()
                moveTo(8.4f, 10.5f); lineTo(8.4f, 8f)
                arcTo(3.6f, 3.6f, 0f, false, true, 15.6f, 8f)
                lineTo(15.6f, 10.5f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        strokeIcon("Trash") {
            stroke {
                moveTo(4f, 6.8f); lineTo(20f, 6.8f)
                moveTo(9.5f, 6.8f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 6.8f)
                moveTo(6.6f, 6.8f); lineTo(7.6f, 20f); lineTo(16.4f, 20f); lineTo(17.4f, 6.8f)
                moveTo(10.5f, 10.5f); lineTo(10.9f, 16.5f)
                moveTo(13.5f, 10.5f); lineTo(13.1f, 16.5f)
            }
        }
    }

    val Edit: ImageVector by lazy {
        strokeIcon("Edit") {
            stroke {
                moveTo(4.5f, 19.5f); lineTo(4.5f, 15.6f)
                lineTo(15.8f, 4.3f); lineTo(19.7f, 8.2f); lineTo(8.4f, 19.5f)
                close()
                moveTo(13.2f, 6.9f); lineTo(17.1f, 10.8f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        strokeIcon("Clock") {
            stroke {
                moveTo(3.5f, 12f)
                arcTo(8.5f, 8.5f, 0f, true, true, 20.5f, 12f)
                arcTo(8.5f, 8.5f, 0f, true, true, 3.5f, 12f)
                close()
                moveTo(12f, 7.2f); lineTo(12f, 12.2f); lineTo(15.8f, 14f)
            }
        }
    }

    val Image: ImageVector by lazy {
        strokeIcon("Image") {
            stroke {
                moveTo(5f, 5f); lineTo(19f, 5f)
                quadTo(20.5f, 5f, 20.5f, 6.5f); lineTo(20.5f, 17.5f)
                quadTo(20.5f, 19f, 19f, 19f); lineTo(5f, 19f)
                quadTo(3.5f, 19f, 3.5f, 17.5f); lineTo(3.5f, 6.5f)
                quadTo(3.5f, 5f, 5f, 5f)
                close()
                moveTo(7.4f, 10.6f)
                arcTo(1.7f, 1.7f, 0f, true, true, 10.8f, 10.6f)
                arcTo(1.7f, 1.7f, 0f, true, true, 7.4f, 10.6f)
                close()
                moveTo(4.2f, 16.5f); lineTo(9.4f, 12f); lineTo(12.6f, 14.8f)
                lineTo(15.4f, 12.4f); lineTo(19.8f, 16.2f)
            }
        }
    }

    /** Connection state. A filled dot, used with a label so colour is never alone. */
    val Dot: ImageVector by lazy {
        strokeIcon("Dot") {
            solid {
                moveTo(6f, 12f)
                arcTo(6f, 6f, 0f, true, true, 18f, 12f)
                arcTo(6f, 6f, 0f, true, true, 6f, 12f)
                close()
            }
        }
    }

    val Branch: ImageVector by lazy {
        strokeIcon("Branch") {
            stroke {
                moveTo(7f, 7.4f); lineTo(7f, 16.6f)
                moveTo(9.4f, 5.6f); lineTo(13.4f, 5.6f)
                arcTo(5.4f, 5.4f, 0f, false, true, 17f, 11.4f)
                lineTo(17f, 12f)
            }
            light {
                moveTo(7f, 5.6f)
                arcTo(2.2f, 2.2f, 0f, true, true, 7f, 5.61f)
                close()
                moveTo(7f, 18.4f)
                arcTo(2.2f, 2.2f, 0f, true, true, 7f, 18.41f)
                close()
                moveTo(17f, 12f)
                arcTo(2.2f, 2.2f, 0f, true, true, 17f, 12.01f)
                close()
            }
        }
    }

    private fun ImageVector.Builder.light(block: PathBuilder.() -> Unit) = strokeLight(block)
}

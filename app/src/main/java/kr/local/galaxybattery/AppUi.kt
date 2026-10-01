package kr.local.galaxybattery

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

/** Shared dimensions prevent theme-specific Button insets and mismatched card/menu edges. */
object AppUi {
    const val PAGE_MARGIN = 20
    const val ACTION_HEIGHT = 48
    const val GAP = 12
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()

    fun card(context: Context, palette: AppPalette) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 18))
        background = GradientDrawable().apply {
            setColor(palette.card); cornerRadius = dp(context, 24).toFloat()
            setStroke(dp(context, 1), (palette.muted and 0xFFFFFF) or (28 shl 24))
        }
    }

    fun action(context: Context, palette: AppPalette, label: String, primary: Boolean = false, clicked: () -> Unit): Button =
        Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            text = label; isAllCaps = false; gravity = Gravity.CENTER
            setTextColor(if (primary) palette.onAccent else palette.accent)
            textSize = 14f; setSingleLine(true)
            setHorizontallyScrolling(false)
            setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            setPadding(dp(context, 12), 0, dp(context, 12), 0)
            stateListAnimator = null; elevation = 0f; backgroundTintList = null
            val shape = GradientDrawable().apply {
                setColor(if (primary) palette.accent else palette.actionSurface)
                cornerRadius = dp(context, 24).toFloat()
            }
            background = RippleDrawable(ColorStateList.valueOf((palette.foreground and 0xFFFFFF) or (35 shl 24)), shape, null)
            setOnClickListener { clicked() }
        }

    fun show(dialog: AlertDialog): AlertDialog {
        dialog.show()
        val context = dialog.context
        // Configuration width follows split-screen bounds and excludes system insets.
        val width = dp(context, (context.resources.configuration.screenWidthDp - PAGE_MARGIN * 2).coerceAtMost(560))
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }
}

fun AlertDialog.Builder.showUniform(): AlertDialog = AppUi.show(create())

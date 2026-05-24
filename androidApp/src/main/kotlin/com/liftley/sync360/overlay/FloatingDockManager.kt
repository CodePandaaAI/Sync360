package com.liftley.sync360.overlay

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.liftley.sync360.core.designsystem.AppTheme

@SuppressLint("ViewConstructor")
class ComposeOverlayLifecycleOwner(context: Context) : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        this.setViewTreeLifecycleOwner(this)
        this.setViewTreeViewModelStoreOwner(this)
        this.setViewTreeSavedStateRegistryOwner(this)
        savedStateRegistryController.performRestore(Bundle())
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun dispose() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class FloatingDockManager(
    private val context: Context,
    private val onClipboardSend: (String) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayContainer: ComposeOverlayLifecycleOwner? = null
    private var isShowing = false

    private val bubbleSizePx = dpToPx(56)

    private val layoutParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams(
            bubbleSizePx,
            bubbleSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dpToPx(200)
        }
    }

    fun show() {
        if (isShowing) return
        
        val container = ComposeOverlayLifecycleOwner(context)
        val composeView = ComposeView(context).apply {
            setContent {
                AppTheme {
                    OverlayContent(
                        onExpand = { expandOverlay(container) },
                        onCollapse = { collapseOverlay(container) },
                        onSendText = {
                            readCurrentClipboardText()?.let(onClipboardSend)
                        }
                    )
                }
            }
        }
        
        container.addView(composeView)
        windowManager.addView(container, layoutParams)
        overlayContainer = container
        isShowing = true
    }

    fun hide() {
        if (!isShowing) return
        overlayContainer?.dispose()
        overlayContainer?.let { windowManager.removeView(it) }
        overlayContainer = null
        isShowing = false
    }

    private fun expandOverlay(view: View) {
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        layoutParams.x = 0
        layoutParams.y = 0
        windowManager.updateViewLayout(view, layoutParams)
    }

    private fun collapseOverlay(view: View) {
        layoutParams.width = bubbleSizePx
        layoutParams.height = bubbleSizePx
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager.updateViewLayout(view, layoutParams)
    }

    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
    ).toInt()

    private fun readCurrentClipboardText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return null
        val description = clipboard.primaryClipDescription ?: return null
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) {
            return null
        }
        return clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }
}

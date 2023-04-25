package ptq.mpga.pinance.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "PTQBookPageView"

data class PTQBookPageViewConfig(
    val pageColor: Color,
)

val LocalPTQBookPageViewConfig = compositionLocalOf<PTQBookPageViewConfig> { error("Local flipper config error") }

private class PTQBookPageViewScopeImpl : PTQBookPageViewScope {
    var contentsBlock: @Composable BoxScope.(currentPage: Int, refresh: () -> Unit) -> Unit = { _, _ -> }
    var pageWantToChangeBlock: (Int, Boolean, Boolean) -> Unit = { _, _, _ -> }
    var onTapBehavior: ((leftUp: Offset, rightDown: Offset, touchPoint: Offset) -> Boolean?)? = null
    var onDragBehavior: ((leftUp: Offset, rightDown: Offset, startTouchPoint: Offset, endTouchPoint: Offset, isRightToLeft: Boolean) -> Boolean)? = null

    override fun onPageWantToChange(block: (currentPage: Int, nextOrPrevious: Boolean, success: Boolean) -> Unit) {
        pageWantToChangeBlock = block
    }

    override fun contents(block: @Composable BoxScope.(currentPage: Int, refresh: () -> Unit) -> Unit) {
        contentsBlock = block
    }

    override fun onTapBehavior(block: (leftUp: Offset, rightDown: Offset, touchPoint: Offset) -> Boolean?) {
        onTapBehavior = block
    }

    override fun onDragBehavior(block: (leftUp: Offset, rightDown: Offset, startTouchPoint: Offset, endTouchPoint: Offset, isRightToLeft: Boolean) -> Boolean) {
        onDragBehavior = block
    }
}

/**
 * @param modifier 修饰符
 * @param pageColor 背页页面颜色
 * @param state 设置页面总数和当前页数，如果页面总数小于当前页数，则会引发异常，具体参见[PTQBookPageViewState]以及[rememberPTQBookPageViewState]
 * @param ptqBookPageViewScope 翻页器提供的各类回调 [PTQBookPageViewScope]
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PTQBookPageView(
    modifier: Modifier = Modifier, pageColor: Color = Color.White, state: PTQBookPageViewState, ptqBookPageViewScope: PTQBookPageViewScope.() -> Unit
) {
    Box(
        modifier.fillMaxSize()
    ) {
        var size by remember { mutableStateOf(IntSize.Zero) }

        val controller by remember {
            mutableStateOf(PTQBookPageBitmapController(state.pageCount))
        }

        val currentPage by remember(state) { mutableStateOf(state.currentPage) }

        remember(state) {
            controller.totalPage = state.pageCount
            currentPage?.let {
                controller.needBitmapAt(it)
            }
            derivedStateOf {  }
        }

        val ptqPageFlipperScopeImpl = rememberUpdatedState(newValue = PTQBookPageViewScopeImpl().apply(ptqBookPageViewScope))

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                object : AbstractComposeView(context) {

                    @Composable
                    override fun Content() {
                        //重组触发器
                        var recomposeTrigger by remember { mutableStateOf(0L) }

                        controller.exeRecompositionBlock = {
                            recomposeTrigger = System.currentTimeMillis()
                        }

                        Box(
                            Modifier
                                .wrapContentSize()
                                .onSizeChanged {
                                    size = it
                                }
                        ) {
                            ptqPageFlipperScopeImpl.value.contentsBlock(this, controller.getNeedPage()) { controller.refresh() }
                            Text(recomposeTrigger.toString(), color = Color.Transparent)
                            invalidate()
                        }
                    }

                    override fun dispatchDraw(canvas: Canvas?) {
                        if (width == 0 || height == 0) return
                        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas2 = Canvas(source)
                        super.dispatchDraw(canvas2)
                        canvas2.setBitmap(null)
                        controller.saveRenderedBitmap(source)
                    }
                }
            }
        )

        CompositionLocalProvider(
            LocalPTQBookPageViewConfig provides PTQBookPageViewConfig(pageColor = pageColor)
        ) {
            Box(
                Modifier
                    .width(size.width.dp)
                    .height(size.height.dp)
                    .align(Alignment.Center)
                    .clipToBounds()
            ) {
                PTQBookPageViewInner(controller = controller, content = ptqPageFlipperScopeImpl.value.contentsBlock, onNext = {
                    if (currentPage == null && controller.currentPage < controller.totalPage - 1) {
                        controller.needBitmapAt(controller.currentPage + 1)
                        ptqPageFlipperScopeImpl.value.pageWantToChangeBlock(controller.currentPage, true, true)
                    } else {
                        ptqPageFlipperScopeImpl.value.pageWantToChangeBlock(controller.currentPage, true, controller.currentPage < controller.totalPage - 1)
                    }
                }, onPrevious = {
                    if (currentPage == null && controller.currentPage > 0) {
                        controller.needBitmapAt(controller.currentPage - 1)
                        ptqPageFlipperScopeImpl.value.pageWantToChangeBlock(controller.currentPage, false, true)
                    } else {
                        ptqPageFlipperScopeImpl.value.pageWantToChangeBlock(controller.currentPage, false, controller.currentPage > 0)
                    }
                })
            }
        }
    }
}

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

interface PTQBookPageViewScope {
    /**
     * 当用户有想翻页的操作时，会触发此回调。如果处于最后一页，仍想向右翻，则翻页失败，但此回调仍然会调用，可以利用这个回调弹Toast显示没有下一页了
     * @param currentPage 用户操作之后的页面索引，范围是0~pageCount-1
     * @param nextOrPrevious 用户想向前翻页还是向后翻页
     * @param success 用户翻页是否成功，处于最后一页还想向右翻则翻页失败，处于第一页向前翻同理
     */
    fun onPageWantToChange(block: (currentPage: Int, nextOrPrevious: Boolean, success: Boolean) -> Unit)

    /**
     * 页面的内容
     * @param currentPage 表示当前显示的页面索引，范围是0~pageCount-1
     * @param refresh 请在contents的最底部手动调用[refresh]以保证视图正确性
     */
    fun contents(block: @Composable BoxScope.(currentPage: Int, refresh: () -> Unit) -> Unit)

    /**
     * 跳转到第几页
     */
    fun jumpTo(pageIndex: Int)
}

private class PTQBookPageViewScopeImpl(private val controller: PTQBookPageBitmapController) : PTQBookPageViewScope {
    var contentsBlock: @Composable BoxScope.(currentPage: Int, refresh: () -> Unit) -> Unit = { _, _ -> }
    var pageWantToChangeBlock: (Int, Boolean, Boolean) -> Unit = { _, _, _ -> }

    override fun onPageWantToChange(block: (currentPage: Int, nextOrPrevious: Boolean, success: Boolean) -> Unit) {
        pageWantToChangeBlock = block
    }

    override fun contents(block: @Composable BoxScope.(currentPage: Int, refresh: () -> Unit) -> Unit) {
        contentsBlock = block
    }

    override fun jumpTo(pageIndex: Int) {
        controller.needBitmapAt(pageIndex)
    }
}

/**
 * @param modifier 修饰符
 * @param pageColor 背页页面颜色
 * @param pageCount 页面总数
 * @param ptqBookPageViewScope 翻页器提供的各类回调 [PTQBookPageViewScope]
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PTQBookPageView(
    modifier: Modifier = Modifier, pageColor: Color = Color.White, @IntRange(from = 1) pageCount: Int = 1, ptqBookPageViewScope: PTQBookPageViewScope.() -> Unit
) {
    require(pageCount > 0) { "pageCount必须大于0" }

    Box(
        modifier.fillMaxSize()
    ) {
        var currentPage by remember { mutableStateOf(0) }
        var size by remember { mutableStateOf(IntSize.Zero) }

        val controller by remember {
            mutableStateOf(PTQBookPageBitmapController(pageCount))
        }

        val ptqPageFlipperScopeImpl = rememberUpdatedState(newValue = PTQBookPageViewScopeImpl(controller).apply(ptqBookPageViewScope))

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                object : AbstractComposeView(context) {

                    @Composable
                    override fun Content() {
                        var recomposeTrigger by remember { mutableStateOf(0L) }

                        controller.exeRecompositionBlock = {
                            recomposeTrigger = System.currentTimeMillis()
                        }

                        Log.d(TAG, "Content:")

                        Box(
                            Modifier
                                .wrapContentSize()
                                .onSizeChanged {
                                    size = it
                                }
                        ) {
//                            ptqPageFlipperScopeImpl.value.contentsBlockBitmap(this, controller.getNeedPage(), controller.exeNeedBitmapAt)
                            ptqPageFlipperScopeImpl.value.contentsBlock(this, controller.getNeedPage()) {
                                controller.refresh()
                            }
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
            }, update = {
                Log.d(TAG, "PTQBookPageView: aaaaa")
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
                    if (currentPage < pageCount - 1) {
                        currentPage++
                        controller.needBitmapAt(currentPage)
                        ptqPageFlipperScopeImpl.value.pageWantToChangeBlock(currentPage, true, true)
                        return@PTQBookPageViewInner
                    }
                    ptqPageFlipperScopeImpl.value.pageWantToChangeBlock(currentPage, true, false)
                }, onPrevious = {
                    if (currentPage > 0) {
                        currentPage--
                        controller.needBitmapAt(currentPage)
                        ptqPageFlipperScopeImpl.value.pageWantToChangeBlock(currentPage, false, true)
                        return@PTQBookPageViewInner
                    }
                    ptqPageFlipperScopeImpl.value.pageWantToChangeBlock(currentPage, false, false)
                })
            }
        }
    }
}












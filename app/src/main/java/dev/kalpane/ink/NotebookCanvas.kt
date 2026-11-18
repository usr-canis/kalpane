package dev.kalpane.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.*
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.ink.authoring.*
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.*
import dev.kalpane.R
import dev.kalpane.Tool
import dev.kalpane.ui.NoteTypefaces
import dev.kalpane.data.*
import kotlinx.coroutines.*
import kotlin.math.*

/** Compose hosts this AndroidX Ink view to retain MotionEvent tool types and cancellation flags. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class NotebookCanvas(context: Context, private val store: DiskStore) : FrameLayout(context) {
    var onEdit: (Page) -> Unit = {}
    var onStroke: (String, Layer) -> Unit = { _, _ -> }
    var onTurn: (Int) -> Unit = {}
    var onLayerMenu: (Layer) -> Unit = {}
    var onTextEdit: (Layer) -> Unit = {}
    var onTextTap: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> }
    internal val hasActiveStroke: Boolean get() = activePointer != null
    var page: Page? = null; private set
    var tool: Tool = Tool.Pen
    var color: Int = 0xff252522.toInt()
    var penWidth = 3f
    var fingerDrawing = false
    var dark = false
    var gridSpacing = 28f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = InputPolicy()
    private val live = InProgressStrokesView(context)
    private val dry = object : View(context) { override fun onDraw(canvas: Canvas) { render(canvas) } }
    private val renderer = CanvasStrokeRenderer.create()
    private val strokes = mutableMapOf<String, Stroke>()
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private val loading = mutableSetOf<String>()
    private val matrix = Matrix()
    private val inverse = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val kannadaFont = ResourcesCompat.getFont(context,R.font.mallige)
    private var zoom = 1f
    private var panX = 0f; private var panY = 0f
    private var fit = 1f
    private var selected: String? = null
    private var draft: Layer? = null
    private var downX = 0f; private var downY = 0f; private var downTime = 0L
    private var lastX = 0f; private var lastY = 0f
    private var activePointer: Int? = null
    private var activeStroke: InProgressStrokeId? = null
    private var activeIsPen = false
    private val pending = mutableMapOf<InProgressStrokeId, Triple<String,Int,Float>>()
    private var resizing = false
    private var touchedMultiple = false
    private var lastFocusX=0f; private var lastFocusY=0f
    private var erased = mutableSetOf<String>()
    private val erasePath = mutableListOf<Pair<Float,Float>>()
    private var hoverX: Float? = null; private var hoverY: Float? = null
    private val longPress = Runnable { if(draft == null && activePointer == null) page?.layers?.find { it.id == selected }?.let(onLayerMenu) }
    private val scale = ScaleGestureDetector(context,object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFocusX=detector.focusX; lastFocusY=detector.focusY
            return policy.touchAllowed(SystemClock.uptimeMillis())
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if(!policy.touchAllowed(SystemClock.uptimeMillis())) return false
            val old = zoom; zoom = (zoom * detector.scaleFactor).coerceIn(1f,5f)
            val ratio = zoom / old
            panX = detector.focusX - width / 2f - (detector.focusX - width / 2f - panX) * ratio
            panY = detector.focusY - height / 2f - (detector.focusY - height / 2f - panY) * ratio
            panX += detector.focusX-lastFocusX; panY += detector.focusY-lastFocusY
            lastFocusX=detector.focusX; lastFocusY=detector.focusY
            if(zoom == 1f) { panX = 0f; panY = 0f }; transform(); dry.invalidate(); return true
        }
    })
    init {
        setBackgroundColor(Color.TRANSPARENT)
        addView(dry,LayoutParams(-1,-1)); addView(live,LayoutParams(-1,-1))
        contentDescription = "Notebook paper. Use the pen to write. Select objects to move or resize. Page controls are below."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        live.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(finished: Map<InProgressStrokeId, Stroke>) {
                finished.forEach { (id,stroke) ->
                    pending.remove(id)?.let { (pageId,strokeColor,strokeWidth) ->
                        val sample = StrokeInput()
                        val points = (0 until stroke.inputs.size).map { i ->
                            stroke.inputs.populate(i,sample)
                            InkPoint(sample.x,sample.y,sample.elapsedTimeMillis,sample.pressure,sample.tiltRadians,sample.orientationRadians,sample.strokeUnitLengthCm)
                        }
                        val layer = Layer(kind = LayerKind.Ink,color = strokeColor,penWidth = strokeWidth,points = points,stylus = stroke.inputs.getToolType() == InputToolType.STYLUS)
                        strokes[layer.id] = stroke
                        if(page?.id == pageId) page=page!!.copy(layers=page!!.layers+layer)
                        onStroke(pageId,layer)
                    }
                }
                dry.invalidate(); live.removeFinishedStrokes(finished.keys)
            }
        })
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Prepare the front buffer before the first pen contact, avoiding first-stroke latency.
        live.eagerInit()
    }
    fun bind(p: Page, newTool: Tool, newColor: Int, width: Float, fingers: Boolean, night: Boolean, spacing: Float) {
        if(page?.id != p.id) {
            cancelActive(); selected = null; draft = null; zoom = 1f; panX = 0f; panY = 0f
            strokes.clear(); bitmaps.clear(); loading.clear()
        }
        page = p; tool = newTool; color = newColor; penWidth = width; fingerDrawing = fingers; dark = night; gridSpacing = spacing
        p.layers.forEach { layer ->
            if(layer.kind == LayerKind.Image && layer.asset !in bitmaps && loading.add(layer.asset)) {
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) { runCatching {
                        ImageAssets.decode(store.asset(layer.asset),2048)
                    }.getOrNull() }
                    if(page?.id == p.id && bitmap != null) { bitmaps[layer.asset] = bitmap; dry.invalidate() }
                    loading.remove(layer.asset)
                }
            }
            if(layer.kind == LayerKind.Ink && layer.id !in strokes && loading.add(layer.id)) {
                scope.launch {
                    val stroke = withContext(Dispatchers.Default) { runCatching { rebuildInk(layer) }.getOrNull() }
                    if(page?.id == p.id && stroke != null) { strokes[layer.id] = stroke; dry.invalidate() }
                    loading.remove(layer.id)
                }
            }
        }
        transform(); dry.invalidate()
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) { if(oldw>0 && oldh>0 && (w!=oldw || h!=oldh)) cancelActive(); super.onSizeChanged(w,h,oldw,oldh); transform() }
    private fun transform() {
        val p = page ?: return
        fit = min((width - 20 * resources.displayMetrics.density) / p.width,(height - 16 * resources.displayMetrics.density) / p.height).coerceAtLeast(.01f)
        val s = fit * zoom
        matrix.reset(); matrix.setScale(s,s); matrix.postTranslate((width-p.width*s)/2+panX,(height-p.height*s)/2+panY); matrix.invert(inverse)
    }
    private fun point(x: Float,y: Float): PointF { val p = floatArrayOf(x,y); inverse.mapPoints(p); return PointF(p[0],p[1]) }
    fun viewPoint(x: Float,y: Float): PointF { val p=floatArrayOf(x,y);matrix.mapPoints(p);return PointF(p[0],p[1]) }
    fun pageDistance(pixels: Float): Float = pixels/(fit*zoom).coerceAtLeast(.01f)
    private fun render(canvas: Canvas) {
        val p = page ?: return
        canvas.save(); canvas.concat(matrix)
        paint.color = 0xffFBF9F4.toInt(); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f,0f,p.width,p.height,6f,6f,paint)
        canvas.clipRect(0f,0f,p.width,p.height)
        paint.color = 0xffD9D9D0.toInt(); paint.strokeWidth = 1f
        val spacing = gridSpacing.coerceIn(12f,80f)
        when(p.paper) {
            Paper.Dots -> { var x=spacing; while(x<p.width) { var y=spacing; while(y<p.height) { canvas.drawCircle(x,y,1.3f,paint); y+=spacing }; x+=spacing } }
            Paper.Grid, Paper.Ruled -> { var y=spacing; while(y<p.height) { canvas.drawLine(0f,y,p.width,y,paint); y+=spacing }; if(p.paper==Paper.Grid) { var x=spacing; while(x<p.width) { canvas.drawLine(x,0f,x,p.height,paint); x+=spacing } } }
            Paper.Plain -> Unit
        }
        p.layers.forEach { original ->
            val l = if(draft?.id == original.id) draft!! else original
            if(l.id in erased) return@forEach
            when(l.kind) {
                LayerKind.Ink -> strokes[l.id]?.let { renderer.draw(canvas,it,matrix) } ?: run {
                    // Keep a vector preview visible while native geometry rebuilds off-thread.
                    val path=Path()
                    l.points.forEachIndexed {i,p->if(i==0) path.moveTo(p.x,p.y) else path.lineTo(p.x,p.y)}
                    paint.color=l.color;paint.strokeWidth=l.penWidth;paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND
                    canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
                }
                LayerKind.Image -> {
                    val rect = RectF(l.x,l.y,l.x+l.width,l.y+l.height)
                    bitmaps[l.asset]?.let { paint.color = Color.WHITE; canvas.drawBitmap(it,null,rect,paint) } ?: run { paint.color = 0xffbbbcb3.toInt(); canvas.drawRect(rect,paint) }
                }
                LayerKind.Text -> {
                    textPaint.color = l.color; textPaint.textSize = l.fontSize
                    textPaint.typeface = NoteTypefaces.get(context,l.textFace)
                    val display=markdownDisplay(l.text)
                    val layout = StaticLayout.Builder.obtain(display,0,display.length,textPaint,l.width.toInt().coerceAtLeast(1)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).build()
                    canvas.save(); canvas.translate(l.x,l.y); layout.draw(canvas); canvas.restore()
                }
            }
        }
        if(tool == Tool.Select) p.layers.find { it.id == selected }?.let { original ->
            val l = draft ?: original
            if(l.kind != LayerKind.Ink) {
                paint.color=0xffE0730C.toInt(); paint.strokeWidth=2f/(fit*zoom); paint.style=Paint.Style.STROKE
                canvas.drawRect(l.x,l.y,l.x+l.width,l.y+l.height,paint); paint.style=Paint.Style.FILL
                if(!l.locked) canvas.drawCircle(l.x+l.width,l.y+l.height,8f/(fit*zoom),paint)
            }
        }
        hoverX?.let { x -> hoverY?.let { y -> val pos=point(x,y); paint.color=color; paint.style=Paint.Style.STROKE; paint.strokeWidth=1f/(fit*zoom); canvas.drawCircle(pos.x,pos.y,penWidth/2,paint); paint.style=Paint.Style.FILL } }
        canvas.restore()
    }
    override fun onInterceptTouchEvent(ev: MotionEvent) = true
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if(event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
            policy.pen(event.eventTime,false)
            hoverX = if(event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) null else event.x; hoverY=event.y; dry.invalidate(); return true
        }
        return super.onHoverEvent(event)
    }
    private fun cancelActive() {
        activePointer?.let { id -> val event=objectEvent(); live.cancelStroke(event,id); event.recycle() }
        activeStroke?.let {pending.remove(it)};activeStroke=null;activePointer=null; draft=null; erased.clear(); removeCallbacks(longPress)
    }
    private fun objectEvent(): MotionEvent = MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,0f,0f,0)
    private fun cancelInk(event: MotionEvent) { activePointer?.let { live.cancelStroke(event,it) };activeStroke?.let {pending.remove(it)};activeStroke=null; activePointer=null }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val action=e.actionMasked; val index=e.actionIndex; val pointer=e.getPointerId(index)
        val penIndices=(0 until e.pointerCount).filter { e.getToolType(it) == MotionEvent.TOOL_TYPE_STYLUS || e.getToolType(it) == MotionEvent.TOOL_TYPE_ERASER }
        val isPen=index in penIndices
        if(action==MotionEvent.ACTION_CANCEL) {
            cancelInk(e); policy.cancelled(); policy.pen(e.eventTime,false); draft=null; erased.clear(); erasePath.clear(); removeCallbacks(longPress); dry.invalidate(); return true
        }
        if(e.flags and MotionEvent.FLAG_CANCELED != 0) {
            // A rejected palm can carry FLAG_CANCELED on POINTER_UP while the pen stays down.
            // Cancel only that pointer's stroke; ACTION_CANCEL above cancels the whole gesture.
            if(activePointer==pointer) {cancelInk(e);if(isPen) policy.pen(e.eventTime,false)}
            policy.cancelled();draft=null;if(activePointer==null) erased.clear();removeCallbacks(longPress);dry.invalidate();return true
        }
        if(penIndices.isNotEmpty()) {
            policy.pen(e.eventTime,!(isPen && (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP)))
            if(activePointer != null && !activeIsPen) cancelInk(e)
            // A new pen contact supersedes a finger selection. Keep the pen's own
            // move/resize draft until UP so its final position can be committed.
            if(isPen && (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN)) {
                draft=null; removeCallbacks(longPress)
            }
        }
        if(action==MotionEvent.ACTION_DOWN) { policy.beginTouch(e.eventTime); touchedMultiple=false; erased.clear(); erasePath.clear(); downX=e.x; downY=e.y; downTime=e.eventTime; lastX=e.x; lastY=e.y }
        if(action==MotionEvent.ACTION_POINTER_DOWN && !isPen) { touchedMultiple=true; if(!activeIsPen) cancelInk(e); draft=null; removeCallbacks(longPress) }
        val active=activePointer
        if(active!=null) {
            when(action) {
                MotionEvent.ACTION_MOVE -> live.addToStroke(e,active,null)
                MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP -> if(pointer==active) { live.finishStroke(e,active); activePointer=null;activeStroke=null }
            }
            return true
        }
        if((action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) && (isPen || policy.drawFinger(e.eventTime,fingerDrawing,e.pointerCount,e.getSize(index))) && tool==Tool.Pen && e.getToolType(index)!=MotionEvent.TOOL_TYPE_ERASER) {
            val p=page ?: return true
            val at=point(e.getX(index),e.getY(index))
            if(at.x in 0f..p.width && at.y in 0f..p.height) {
                requestUnbufferedDispatch(e); parent?.requestDisallowInterceptTouchEvent(true)
                activePointer=pointer; activeIsPen=isPen
                val id=live.startStroke(e,pointer,inkBrush(color,penWidth),inverse)
                activeStroke=id;pending[id]=Triple(p.id,color,penWidth)
            }
            return true
        }
        val allowed=isPen || policy.touchAllowed(e.eventTime)
        if(!allowed) return true
        if(penIndices.isEmpty()) scale.onTouchEvent(e)
        if(e.pointerCount>=2) {
            touchedMultiple=true
            if(action==MotionEvent.ACTION_MOVE && !scale.isInProgress) { panX+=e.x-lastX; panY+=e.y-lastY; transform(); dry.invalidate() }
            lastX=e.x; lastY=e.y; return true
        }
        val pos=point(e.getX(index),e.getY(index)); val p=page ?: return true
        if((tool==Tool.Text || (!isPen && !fingerDrawing && tool!=Tool.Navigate)) && action==MotionEvent.ACTION_UP && !touchedMultiple && hypot(e.x-downX,e.y-downY)<ViewConfiguration.get(context).scaledTouchSlop) {
            if(pos.x in 0f..p.width && pos.y in 0f..p.height) {
                val text=p.layers.lastOrNull {it.kind==LayerKind.Text && !it.locked && pos.x in it.x..(it.x+it.width) && pos.y in it.y..(it.y+it.height)}
                val image=p.layers.lastOrNull {it.kind==LayerKind.Image && pos.x in it.x..(it.x+it.width) && pos.y in it.y..(it.y+it.height)}
                if(tool!=Tool.Select || image==null) {if(text==null) onTextTap(e.x,e.y,pos.x,pos.y) else onTextEdit(text);return true}
            }
        }
        if(tool==Tool.Eraser || tool==Tool.PartialEraser || e.getToolType(index)==MotionEvent.TOOL_TYPE_ERASER) {
            if(isPen || fingerDrawing) {
                if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN || action==MotionEvent.ACTION_MOVE) {
                    if(tool==Tool.PartialEraser) erasePath+=pos.x to pos.y else p.layers.filter { it.kind==LayerKind.Ink && !it.locked && hitsInk(it,pos,18f/(fit*zoom)) }.forEach { erased+=it.id }; dry.invalidate()
                }
                if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) { if(tool==Tool.PartialEraser && erasePath.isNotEmpty()) {val layers=p.layers.flatMap {if(it.kind==LayerKind.Ink && !it.locked) eraseParts(it,erasePath,18f/(fit*zoom)) else listOf(it)};if(layers!=p.layers) onEdit(p.copy(layers=layers))} else if(erased.isNotEmpty()) onEdit(p.copy(layers=p.layers.filterNot { it.id in erased })); erased.clear(); erasePath.clear(); dry.invalidate() }
            }; return true
        }
        if(tool==Tool.Select) {
            when(action) {
                MotionEvent.ACTION_DOWN -> {
                    val current=p.layers.find { it.id==selected && it.kind!=LayerKind.Ink }
                    resizing=current!=null && hypot(pos.x-current.x-current.width,pos.y-current.y-current.height)<24f/(fit*zoom)
                    if(!resizing) selected=p.layers.lastOrNull { l -> if(l.kind==LayerKind.Ink) hitsInk(l,pos,14f/(fit*zoom)) else pos.x in l.x..(l.x+l.width) && pos.y in l.y..(l.y+l.height) }?.id
                    postDelayed(longPress,ViewConfiguration.getLongPressTimeout().toLong()); dry.invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if(hypot(e.x-downX,e.y-downY)>ViewConfiguration.get(context).scaledTouchSlop && !touchedMultiple) {
                        removeCallbacks(longPress)
                        p.layers.find { it.id==selected && !it.locked && it.kind!=LayerKind.Ink }?.let { original ->
                            val start=point(downX,downY); val dx=pos.x-start.x; val dy=pos.y-start.y
                            draft=if(resizing) original.copy(width=(original.width+dx).coerceIn(32f,p.width*2),height=(original.height+dy).coerceIn(32f,p.height*2)) else original.copy(x=(original.x+dx).coerceIn(0f,p.width-20),y=(original.y+dy).coerceIn(0f,p.height-20)); dry.invalidate()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPress)
                    draft?.let { changed -> onEdit(p.copy(layers=p.layers.map { if(it.id==changed.id) changed else it })) }; draft=null; dry.invalidate(); performClick()
                }
            }
        } else if(tool==Tool.Navigate) {
            if(action==MotionEvent.ACTION_MOVE && zoom>1.02f) { panX+=e.x-lastX; panY+=e.y-lastY; transform(); dry.invalidate() }
            if(action==MotionEvent.ACTION_UP && !touchedMultiple && policy.turnPage(e.eventTime,true,zoom,(e.x-downX)/resources.displayMetrics.density,(e.y-downY)/resources.displayMetrics.density,e.eventTime-downTime)) onTurn(if(e.x<downX) 1 else -1)
        }
        lastX=e.x; lastY=e.y; return true
    }
    private fun hitsInk(l: Layer,p: PointF,radius: Float): Boolean {
        val r=radius+l.penWidth/2
        return l.points.zipWithNext().any { (a,b) ->
            val dx=b.x-a.x; val dy=b.y-a.y; val t=if(dx*dx+dy*dy==0f) 0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy)).coerceIn(0f,1f)
            hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)<=r
        } || l.points.any { hypot(p.x-it.x,p.y-it.y)<=r }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { cancelActive(); scope.cancel(); super.onDetachedFromWindow() }
}

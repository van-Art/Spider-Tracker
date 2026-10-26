package com.spidertracker.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import kotlin.math.min
import kotlin.math.floor

@Composable fun rememberReducedMotion():Boolean {
    val resolver=LocalContext.current.contentResolver
    fun disabled()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    var reduced by remember {mutableStateOf(disabled())}
    DisposableEffect(resolver) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean){reduced=disabled()}
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose {resolver.unregisterContentObserver(observer)}
    }
    return reduced
}

data class PixelBounds(val x:Int,val y:Int,val width:Int,val height:Int)
fun pixelBounds(sourceWidth:Int,sourceHeight:Int,width:Int,height:Int):PixelBounds {
    require(sourceWidth>0 && sourceHeight>0 && width>=0 && height>=0)
    var scale=min(width.toFloat()/sourceWidth,height.toFloat()/sourceHeight)
    val integer=floor(scale)
    if(integer>=1 && integer>=scale*.9f)scale=integer
    val w=(sourceWidth*scale).roundToInt().coerceAtMost(width)
    val h=(sourceHeight*scale).roundToInt().coerceAtMost(height)
    return PixelBounds((width-w)/2,(height-h)/2,w,h)
}
private val pixelPaint=android.graphics.Paint().apply {isAntiAlias=false;isFilterBitmap=false}
fun DrawScope.pixelPath(path:androidx.compose.ui.graphics.Path,color:Color,width:Float=0f) {
    pixelPaint.color=color.toArgb();pixelPaint.style=if(width>0)android.graphics.Paint.Style.STROKE else android.graphics.Paint.Style.FILL
    pixelPaint.strokeWidth=width.roundToInt().coerceAtLeast(1).toFloat()
    drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(),pixelPaint)
}
fun DrawScope.pixelLine(color:Color,start:Offset,end:Offset,width:Float=1f) {
    pixelPaint.color=color.toArgb();pixelPaint.style=android.graphics.Paint.Style.STROKE
    val stroke=width.roundToInt().coerceAtLeast(1);pixelPaint.strokeWidth=stroke.toFloat()
    val half=if(stroke%2==1).5f else 0f
    drawContext.canvas.nativeCanvas.drawLine(start.x.roundToInt()+half,start.y.roundToInt()+half,end.x.roundToInt()+half,end.y.roundToInt()+half,pixelPaint)
}
fun DrawScope.pixelCircle(color:Color,radius:Float,center:Offset,width:Float=0f) {
    pixelPaint.color=color.toArgb();pixelPaint.style=if(width>0)android.graphics.Paint.Style.STROKE else android.graphics.Paint.Style.FILL
    pixelPaint.strokeWidth=width.roundToInt().coerceAtLeast(1).toFloat()
    drawContext.canvas.nativeCanvas.drawCircle(center.x.roundToInt().toFloat(),center.y.roundToInt().toFloat(),radius.roundToInt().toFloat(),pixelPaint)
}
fun DrawScope.pixelRect(color:Color,topLeft:Offset,size:androidx.compose.ui.geometry.Size) {
    pixelPaint.color=color.toArgb();pixelPaint.style=android.graphics.Paint.Style.FILL
    drawContext.canvas.nativeCanvas.drawRect(topLeft.x.roundToInt().toFloat(),topLeft.y.roundToInt().toFloat(),
        (topLeft.x+size.width).roundToInt().toFloat(),(topLeft.y+size.height).roundToInt().toFloat(),pixelPaint)
}

@Composable fun PixelSearchBackground(modifier:Modifier) {
    val bitmap=suppliedBitmap(R.drawable.search_bar)
    Canvas(modifier) {
        val h=size.height.roundToInt();val w=size.width.roundToInt()
        val cap=(7f*h/bitmap.height).roundToInt().coerceAtMost(w/2)
        fun part(sx:Int,sw:Int,dx:Int,dw:Int) {
            if(dw>0)drawImage(bitmap,IntOffset(sx,0),IntSize(sw,bitmap.height),IntOffset(dx,0),IntSize(dw,h),filterQuality=FilterQuality.None)
        }
        part(0,7,0,cap);part(7,bitmap.width-14,cap,w-cap*2);part(bitmap.width-7,7,w-cap,cap)
    }
}

internal class ForegroundAnimationClock {
    var seconds by mutableFloatStateOf(0f); private set
    suspend fun run(lifecycle:Lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=SystemClock.elapsedRealtime()
            while(true){delay(33);val now=SystemClock.elapsedRealtime();seconds+=(now-previous)/1000f;previous=now}
        }
    }
}

data class SpriteFrame(val x:Int,val width:Int,val height:Int)
fun extractSpriteFrames(occupiedColumns:BooleanArray,height:Int):List<SpriteFrame> {
    require(height>0 && occupiedColumns.isNotEmpty())
    val runs=mutableListOf<IntRange>();var start=-1
    for(x in 0..occupiedColumns.size) {
        val occupied=x<occupiedColumns.size && occupiedColumns[x]
        if(occupied && start<0)start=x
        if(!occupied && start>=0){runs.add(start until x);start=-1}
    }
    require(runs.isNotEmpty()) {"Sprite sheet has no visible frames"}
    val width=(runs.maxOf {it.last-it.first+1}+2).coerceAtMost(occupiedColumns.size)
    return runs.map {SpriteFrame((it.first-1).coerceIn(0,occupiedColumns.size-width),width,height)}
}
class SpriteAtlas(val image:ImageBitmap,val frames:List<SpriteFrame>)
val SpriteFrameKey=SemanticsPropertyKey<Int>("SpriteFrame")

@Composable fun suppliedBitmap(resource:Int):ImageBitmap {
    val resources=LocalResources.current
    return remember(resources,resource) {BitmapFactory.decodeResource(resources,resource,BitmapFactory.Options().apply {inScaled=false}).asImageBitmap()}
}
@Composable fun SuppliedImage(resource:Int,description:String?,modifier:Modifier=Modifier) {
    val bitmap=suppliedBitmap(resource)
    Canvas(modifier.semantics {if(description!=null)contentDescription=description}) {
        val b=pixelBounds(bitmap.width,bitmap.height,size.width.roundToInt(),size.height.roundToInt())
        if(b.width>0 && b.height>0)drawImage(bitmap,srcSize=IntSize(bitmap.width,bitmap.height),dstOffset=IntOffset(b.x,b.y),dstSize=IntSize(b.width,b.height),filterQuality=FilterQuality.None)
    }
}
@Composable fun rememberSpriteAtlas():SpriteAtlas {
    val resources=LocalResources.current
    return remember(resources) {
        val bitmap=BitmapFactory.decodeResource(resources,R.drawable.spider_sprite_sheet,BitmapFactory.Options().apply {inScaled=false})
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val columns=BooleanArray(bitmap.width){x->(0 until bitmap.height).any {y->pixels[y*bitmap.width+x] ushr 24!=0}}
        SpriteAtlas(bitmap.asImageBitmap(),extractSpriteFrames(columns,bitmap.height))
    }
}
@Composable fun SpriteCharacter(time:Float,modifier:Modifier=Modifier) {
    val atlas=rememberSpriteAtlas()
    val index=(time*12).toInt().mod(atlas.frames.size)
    val frame=atlas.frames[index]
    Canvas(modifier.testTag("spider-sprite").semantics {contentDescription="Animated pixel character";this[SpriteFrameKey]=index}) {
        drawImage(atlas.image,srcOffset=IntOffset(frame.x,0),srcSize=IntSize(frame.width,frame.height),
            dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),filterQuality=FilterQuality.None)
    }
}

@Composable internal fun AnimatedCharacter(clock:ForegroundAnimationClock,capture:Boolean,reducedMotion:Boolean,modifier:Modifier) {
    val frameTime by remember(clock,capture,reducedMotion) {derivedStateOf {if(reducedMotion)0f else if(capture).7f else (clock.seconds*12).toInt()/12f}}
    SpriteCharacter(frameTime,modifier)
}

package com.spidertracker.app

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

object Ink {
    val Cyan=Color(0xff96e0f7)
    val Pale=Color(0xffa8e1fe)
    val Water=Color(0xff0e172b)
    val Land=Color(0xff043956)
    val Navy=Color(0xf20e1c3b)
    val Cream=Color(0xffeaeadc)
    val Orange=Color(0xfff89d51)
    val Frame=Color(0xff3997bb)
    val Bevel=Color(0xff2976a0)
    val Edge=Color(0xff1e1e14)
    val Charcoal=Color(0xff282828)
    fun pin(kind:PinKind)=when(kind) {
        PinKind.Confirmed->Color(0xff69af7f); PinKind.Rumored->Color(0xffdc6056)
        PinKind.Event->Color(0xff84c9e5); PinKind.Trailer->Cream
    }
}

fun steppedPath(width:Float,height:Float,step:Float=3f):Path = Path().apply {
    moveTo(step*2,0f); lineTo(width-step*2,0f); lineTo(width-step*2,step)
    lineTo(width-step,step); lineTo(width-step,step*2); lineTo(width,step*2)
    lineTo(width,height-step*2); lineTo(width-step,height-step*2); lineTo(width-step,height-step)
    lineTo(width-step*2,height-step); lineTo(width-step*2,height); lineTo(step*2,height)
    lineTo(step*2,height-step); lineTo(step,height-step); lineTo(step,height-step*2)
    lineTo(0f,height-step*2); lineTo(0f,step*2); lineTo(step,step*2); lineTo(step,step); lineTo(step*2,step); close()
}

fun markerBitmap(kind:PinKind, side:Int=64):Bitmap {
    val bitmap=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888)
    val canvas=AndroidCanvas(bitmap)
    canvas.scale(side/16f,side/16f)
    val paint=Paint().apply { isAntiAlias=false }
    fun block(x:Float,y:Float,w:Float,h:Float,color:Int) {
        paint.color=color; canvas.drawRect(x,y,x+w,y+h,paint)
    }
    paint.color=0xff172c30.toInt()
    canvas.drawPath(AndroidPath().apply { moveTo(4f,0f);lineTo(12f,0f);lineTo(16f,4f);lineTo(16f,12f);lineTo(12f,16f);lineTo(4f,16f);lineTo(0f,12f);lineTo(0f,4f);close() },paint)
    paint.color=when(kind) { PinKind.Confirmed->0xff69af7f;PinKind.Rumored->0xffdc6056;PinKind.Event->0xff84c9e5;PinKind.Trailer->0xffeaeadc }.toInt()
    canvas.drawPath(AndroidPath().apply { moveTo(4f,1f);lineTo(12f,1f);lineTo(15f,4f);lineTo(15f,12f);lineTo(12f,15f);lineTo(4f,15f);lineTo(1f,12f);lineTo(1f,4f);close() },paint)
    val dark=0xff15272c.toInt()
    // Original scanner, question, event star and playback glyphs; no site artwork is bundled.
    when(kind) {
        PinKind.Confirmed -> {
            block(6f,4f,4f,8f,dark);block(4f,6f,8f,4f,dark)
            block(7f,7f,2f,2f,0xffa1d5b2.toInt())
            for(x in listOf(3f,11f))for(y in listOf(3f,11f))block(x,y,2f,2f,dark)
        }
        PinKind.Rumored -> {
            listOf(floatArrayOf(5f,3f,6f,2f),floatArrayOf(9f,5f,3f,3f),floatArrayOf(7f,7f,4f,2f),floatArrayOf(7f,8f,2f,2f),floatArrayOf(7f,12f,2f,2f)).forEach { block(it[0],it[1],it[2],it[3],dark) }
        }
        PinKind.Event -> {
            paint.color=dark
            canvas.drawPath(AndroidPath().apply { moveTo(8f,3f);lineTo(10f,6f);lineTo(14f,7f);lineTo(11f,10f);lineTo(12f,14f);lineTo(8f,12f);lineTo(4f,14f);lineTo(5f,10f);lineTo(2f,7f);lineTo(6f,6f);close() },paint)
        }
        PinKind.Trailer -> {
            paint.color=dark;canvas.drawPath(AndroidPath().apply {moveTo(6f,4f);lineTo(12f,8f);lineTo(6f,12f);close()},paint)
        }
    }
    return bitmap
}

@Composable fun PinGlyph(kind:PinKind,modifier:Modifier=Modifier) {
    val bitmap=remember(kind){markerBitmap(kind).asImageBitmap()}
    Image(bitmap,null,modifier,filterQuality=FilterQuality.None)
}

@Composable fun FieldIllustration(index:Int,modifier:Modifier=Modifier,progress:Float=0f) {
    Canvas(modifier) {
        val palettes=listOf(0xff263c4a,0xff503d37,0xff213d50,0xff384338,0xff332f48,0xff40382b)
        drawRect(Color(palettes[index.mod(palettes.size)]))
        val w=size.width;val h=size.height
        drawCircle(Color(0xffb6c7c5).copy(alpha=.45f),w*.12f,Offset(w*.72f,h*.18f))
        repeat(12) { i ->
            val x=i*w/10-w*.1f;val bh=h*(.22f+((i*7+index*3)%9)*.04f)
            drawRect(Color(0xff101b2b),Offset(x,h*.78f-bh),Size(w*.11f,bh))
            repeat(5) { r -> drawRect(Ink.Cyan.copy(alpha=.35f),Offset(x+w*.025f,h*.78f-bh+10+r*bh/6),Size(w*.02f,h*.008f)) }
        }
        drawRect(Color(0xff1c2937),Offset(0f,h*.78f),Size(w,h*.22f))
        drawLine(Color(0xff8b9d9d),Offset(0f,h*.85f),Offset(w,h*.67f),w*.035f)
        val cx=w*(.42f+progress*.12f);val cy=h*.47f
        drawLine(Ink.Orange,Offset(cx-w*.16f,cy),Offset(cx+w*.17f,cy),w*.025f)
        drawRect(Ink.Orange,Offset(cx-w*.07f,cy-h*.025f),Size(w*.14f,h*.05f))
        drawRect(Ink.Cyan,Offset(cx-w*.04f,cy-h*.014f),Size(w*.08f,h*.025f))
        drawCircle(Ink.Cream,w*.015f,Offset(cx,cy+h*.016f))
        repeat(7) { i -> drawLine(Ink.Cyan.copy(alpha=.12f),Offset(0f,h*i/7),Offset(w,h*i/7),1f) }
    }
}

fun DrawScope.drawAgent(time:Float) {
    val u=size.width/22
    fun rect(x:Float,y:Float,w:Float,h:Float,c:Color)=drawRect(c,Offset(x*u,y*u),Size(w*u,h*u))
    val eyeShift=if((time.toInt()%8)<4)0f else 1f
    rect(6f,1f,10f,2f,Ink.Edge);rect(4f,3f,14f,13f,Ink.Edge)
    rect(5f,4f,12f,10f,Ink.Orange);rect(7f,6f,8f,5f,Ink.Water)
    rect(8f+eyeShift,7f,2f,2f,Ink.Cyan);rect(12f+eyeShift,7f,2f,2f,Ink.Cyan)
    rect(8f,0f,2f,4f,Ink.Cyan);rect(6f,16f,10f,10f,Ink.Edge)
    rect(7f,16f,8f,7f,Ink.Frame);rect(3f,17f,3f,8f,Ink.Bevel);rect(16f,17f,3f,8f,Ink.Bevel)
    rect(7f,25f,3f,6f,Ink.Orange);rect(12f,25f,3f,6f,Ink.Orange)
    rect(5f,31f,6f,2f,Ink.Edge);rect(12f,31f,6f,2f,Ink.Edge)
}

package dev.kalpane.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable fun ColorPicker(initial: Int, recent: List<Int>,onDismiss:()->Unit,onPick:(Int)->Unit) {
    val start=remember {FloatArray(3).also {AndroidColor.colorToHSV(initial,it)}}
    var hue by remember {mutableFloatStateOf(start[0])}; var saturation by remember {mutableFloatStateOf(start[1])}; var value by remember {mutableFloatStateOf(start[2])}
    var alpha by remember {mutableFloatStateOf(AndroidColor.alpha(initial)/255f)}
    val color=AndroidColor.HSVToColor((alpha*255).toInt(),floatArrayOf(hue,saturation,value))
    var hex by remember {mutableStateOf("%06X".format(initial and 0xffffff))}
    var red by remember {mutableStateOf(AndroidColor.red(initial).toString())}; var green by remember {mutableStateOf(AndroidColor.green(initial).toString())}; var blue by remember {mutableStateOf(AndroidColor.blue(initial).toString())}
    fun set(c: Int) { val hsv=FloatArray(3);AndroidColor.colorToHSV(c,hsv);hue=hsv[0];saturation=hsv[1];value=hsv[2];alpha=AndroidColor.alpha(c)/255f }
    LaunchedEffect(color) {hex="%06X".format(color and 0xffffff);red=AndroidColor.red(color).toString();green=AndroidColor.green(color).toString();blue=AndroidColor.blue(color).toString()}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Ink color")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(color)).border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(12.dp)).semantics {contentDescription="Selected color #$hex, ${(alpha*100).toInt()} percent opacity"})
        Text("HSV",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=16.dp))
        ColorSlider("Hue",hue,0f..360f,"${hue.toInt()}°") {hue=it}
        ColorSlider("Saturation",saturation,0f..1f,"${(saturation*100).toInt()}%") {saturation=it}
        ColorSlider("Value",value,0f..1f,"${(value*100).toInt()}%") {value=it}
        ColorSlider("Opacity",alpha,0f..1f,"${(alpha*100).toInt()}%") {alpha=it}
        OutlinedTextField(hex,{ input -> hex=input.removePrefix("#").take(6);if(hex.matches(Regex("[0-9a-fA-F]{6}"))) set(((alpha*255).toInt() shl 24) or hex.toInt(16)) },label={Text("HEX · RRGGBB")},prefix={Text("#")},singleLine=true,isError=!hex.matches(Regex("[0-9a-fA-F]{6}")),modifier=Modifier.fillMaxWidth())
        Text("RGB · 0–255",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("R","G","B").forEachIndexed {i,label->
                val str=listOf(red,green,blue)[i]
                OutlinedTextField(str,{input->
                    when(i){0->red=input;1->green=input;else->blue=input}
                    val values=listOf(red,green,blue).map {it.toIntOrNull()}
                    if(values.all {it!=null && it in 0..255}) set(AndroidColor.argb((alpha*255).toInt(),values[0]!!,values[1]!!,values[2]!!))
                },label={Text(label)},singleLine=true,isError=str.toIntOrNull() !in 0..255,modifier=Modifier.weight(1f))
            }
        }
        Text("Recent colors",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {recent.forEach {c-> IconButton({set(c)},Modifier.size(48.dp)) {Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).border(1.dp,MaterialTheme.colorScheme.outline,CircleShape).semantics {contentDescription="Use color #${"%08X".format(c)}"})}}}
    }},confirmButton={TextButton({onPick(color)},enabled=hex.matches(Regex("[0-9a-fA-F]{6}")) && listOf(red,green,blue).all {it.toIntOrNull() in 0..255}){Text("Use color")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
@Composable private fun ColorSlider(label: String,value: Float,range: ClosedFloatingPointRange<Float>,formatted: String,onChange:(Float)->Unit) {
    Row(Modifier.fillMaxWidth()) {Text(label,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f));Text(formatted,style=MaterialTheme.typography.labelSmall)}
    Slider(value,onChange,valueRange=range,modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription=label})
}

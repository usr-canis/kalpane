package dev.kalpane.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.kalpane.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable fun Brand(modifier: Modifier = Modifier, large: Boolean = false) {
    Row(modifier,verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(if(large) 16.dp else 8.dp)) {
        Text("ಕಲ್ಪನೆ",fontFamily = Mallige,fontSize = if(large) 50.sp else 32.sp,color = MaterialTheme.colorScheme.onSurface)
        Image(painterResource(R.drawable.kalpane_logo),"Kalpane feather and scroll",Modifier.size(if(large) 160.dp else 80.dp))
    }
}
@Composable fun ActionIcon(icon: ImageVector, label: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick,modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).then(if(selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier),enabled = enabled) {
        Icon(icon,label,tint = if(!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = .32f) else if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier = Modifier.size(22.dp))
    }
}
@Composable fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss,title = { Text(title) },text = { OutlinedTextField(value,{ value=it },label={Text("Name")},singleLine=true,modifier=Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ onSave(value.trim()); onDismiss() },enabled=value.isNotBlank()) { Text("Save") } },dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
@Composable fun SectionLabel(text: String) { Text(text.uppercase(),style=MaterialTheme.typography.labelSmall,letterSpacing=1.6.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleSmall); Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked,onChange,modifier=Modifier.semantics {contentDescription=title})
    }
}
@Composable fun ListAction(icon: ImageVector,title: String,detail: String? = null,onClick: () -> Unit) {
    Surface(onClick=onClick,color=Color.Transparent,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.bodyLarge); detail?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

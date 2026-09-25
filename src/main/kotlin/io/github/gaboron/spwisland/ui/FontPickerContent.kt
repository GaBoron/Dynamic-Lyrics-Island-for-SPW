// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gaboron.spwisland.core.IslandSettings
import io.github.gaboron.spwisland.core.LyricFontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val WindowDark = Color(0xFF181D27)
private val Surface = Color(0xFF202631)
private val SurfaceLight = Color(0xFF2A303D)
private val Stroke = Color(0xFF424A5B)
private val Accent = Color(0xFF4B82F5)
private val White = Color(0xFFF5F7FC)
private val Muted = Color(0xFFB7C1D2)

@Composable
internal fun FontPickerContent(current: IslandSettings, onCancel: () -> Unit, onApply: (FontPickerSelection) -> Unit) {
    var families by remember { mutableStateOf<List<PickerFamily>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("全部") }
    var selected by remember { mutableStateOf(current.fontFamily) }
    var faceName by remember { mutableStateOf(current.fontFamily) }
    var weight by remember { mutableStateOf(current.fontWeight) }
    var primary by remember { mutableStateOf("Twinkle, twinkle, little star") }
    var translation by remember { mutableStateOf("一闪一闪亮晶晶") }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        failure = null
        runCatching { withContext(Dispatchers.IO) { FontPickerCatalog.load() } }
            .onSuccess { loaded ->
                families = loaded
                if (loaded.none { it.name == selected }) {
                    loaded.firstOrNull { item -> item.faces.any { it.name == selected } }?.let { selected = it.name }
                }
                loaded.firstOrNull { it.name == selected }?.let { item ->
                    pickFace(item, weight, item.faces.firstOrNull { it.name == faceName }?.italic == true)
                        ?.let { face -> faceName = face.name; weight = face.weight }
                }
                families = withContext(Dispatchers.IO) { FontPickerCatalog.localize(loaded) }
            }
            .onFailure { failure = it.message ?: "字体列表读取失败" }
    }
    val family = families.firstOrNull { it.name == selected }
    val matching = families.filter { item ->
        (item.name.contains(search, true) || item.localizedName.contains(search, true)) && when (filter) {
            "简体中文" -> item.category == PickerFontCategory.SIMPLIFIED_CHINESE
            "English" -> item.category == PickerFontCategory.ENGLISH
            "其他" -> item.category == PickerFontCategory.OTHER
            else -> true
        }
    }

    MaterialTheme(colors = darkColors(primary = Accent, background = WindowDark, surface = Surface)) {
        Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF303547), WindowDark,
            Color(0xFF28232E)))).padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Aa", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(14.dp))
                Text("选择字体", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("歌词外观", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.width(325.dp).fillMaxHeight()) {
                    PickerTextField(search, { search = it }, "⌕  搜索字体名称…")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("全部", "简体中文", "English", "其他").forEach { item ->
                            PickerPill(item, filter == item) { filter = item }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Surface)
                        .border(BorderStroke(1.dp, Stroke.copy(alpha = .6f)), RoundedCornerShape(12.dp))) {
                        if (families.isEmpty()) item {
                            Text(failure ?: "正在读取系统字体…", color = Muted,
                                modifier = Modifier.padding(16.dp), fontSize = 13.sp)
                        }
                        items(matching, key = { it.name }) { item ->
                            val active = selected == item.name
                            Column(Modifier.fillMaxWidth()
                                .background(if (active) Color(0xFF2B3B5D) else Surface)
                                .clickable {
                                    selected = item.name
                                    val italic = item.faces.firstOrNull { it.name == faceName }?.italic == true
                                    val face = pickFace(item, weight, italic)
                                    faceName = face?.name ?: item.name
                                    weight = face?.weight ?: LyricFontWeight.REGULAR
                                }
                                .then(if (active) Modifier.border(1.dp, Accent, RoundedCornerShape(8.dp)) else Modifier)
                                .padding(horizontal = 16.dp, vertical = 9.dp)) {
                                Text(if (item.name.isEmpty()) "MiSans" else item.name, color = White,
                                    fontSize = 15.sp, maxLines = 1)
                                val subtitle = if (item.name.isEmpty()) "插件默认 · 多语言回退" else
                                    item.localizedName.takeIf { it != item.name }
                                if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(BorderStroke(1.dp, Stroke.copy(alpha = .55f)), RoundedCornerShape(16.dp))
                    .padding(17.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (selected.isEmpty()) "MiSans" else selected, color = White,
                                fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text(family?.source ?: "正在读取字体信息…", color = Muted,
                                fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("可用字形", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(9.dp))
                        Text("${family?.faces?.size ?: 0} 款", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        family?.faces?.forEach { face ->
                            PickerPill(face.label, face.name == faceName && face.weight == weight) {
                                faceName = face.name
                                weight = face.weight
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(13.dp))
                        .background(SurfaceLight)
                        .padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("预览", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("字号 ${current.fontSize} · 在 SPW 设置中调整", color = Muted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        SwingPanel(factory = { FontPickerPreviewPanel(current) }, update = {
                            it.family = faceName; it.weight = weight; it.fontSize = current.fontSize
                            it.primary = primary; it.translation = translation; it.repaint()
                        }, modifier = Modifier.weight(1f).fillMaxWidth())
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PickerTextField(primary, { primary = it }, "主歌词预览", Modifier.weight(1f))
                            PickerTextField(translation, { translation = it }, "翻译预览", Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ActionButton("⟳  刷新字体列表", false) { refresh++ }
                Spacer(Modifier.weight(1f))
                ActionButton("取消", false, onCancel)
                Spacer(Modifier.width(9.dp))
                ActionButton("确定", true) { onApply(FontPickerSelection(faceName, weight)) }
            }
        }
    }
}

@Composable
private fun PickerTextField(value: String, onChange: (String) -> Unit, hint: String,
                            modifier: Modifier = Modifier) {
    BasicTextField(value, onChange, modifier = modifier.fillMaxWidth()
        .background(SurfaceLight, RoundedCornerShape(8.dp))
        .border(BorderStroke(1.dp, Stroke), RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp), singleLine = true,
        textStyle = TextStyle(color = White, fontSize = 13.sp), cursorBrush = SolidColor(Accent),
        decorationBox = { inner -> Box {
            if (value.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp)
            inner()
        } })
}

@Composable
private fun PickerPill(label: String, active: Boolean, green: Boolean = false, onClick: () -> Unit) {
    val fill = if (green) Color(0xFF244F41) else if (active) Accent else SurfaceLight
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(fill)
        .border(BorderStroke(1.dp, if (active) Accent else Stroke), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = if (green) Color(0xFF8CE6AC) else White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ActionButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(if (primary) Accent else SurfaceLight)
        .border(1.dp, if (primary) Accent else Stroke, RoundedCornerShape(9.dp))
        .clickable(onClick = onClick).padding(horizontal = 23.dp, vertical = 11.dp)) {
        Text(label, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun pickFace(family: PickerFamily?, weight: LyricFontWeight, italic: Boolean): PickerFace? =
    family?.faces?.filter { it.italic == italic }
        ?.minByOrNull { abs(it.weight.storageName.toInt() - weight.storageName.toInt()) }
        ?: family?.faces?.firstOrNull()

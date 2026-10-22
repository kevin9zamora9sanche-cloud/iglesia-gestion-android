package cl.iglesia.gestion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Formato ---------------------------------------------------------------------

val ChileLocale: Locale = Locale("es", "CL")

private fun String.capitalized(): String = replaceFirstChar { it.titlecase(ChileLocale) }

fun LocalDate.longLabel(): String =
    format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ChileLocale)).capitalized()

fun LocalDate.shortLabel(): String =
    format(DateTimeFormatter.ofPattern("EEE d MMM", ChileLocale)).replace(".", "").capitalized()

fun LocalDate.fieldLabel(): String =
    format(DateTimeFormatter.ofPattern("d MMM yyyy", ChileLocale)).replace(".", "")

fun LocalDate.monthLabel(): String =
    format(DateTimeFormatter.ofPattern("MMMM yyyy", ChileLocale)).capitalized()

fun LocalDate.weekdayInitials(): String =
    format(DateTimeFormatter.ofPattern("EEE", ChileLocale)).replace(".", "").take(3).capitalized()

/** "Hoy", "Mañana", "Ayer" o la fecha corta. */
fun LocalDate.relativeLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Hoy"
    today.plusDays(1) -> "Mañana"
    today.minusDays(1) -> "Ayer"
    else -> shortLabel()
}

fun dueLabel(dueDate: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, dueDate)
    return when {
        days == 0L -> "Vence hoy"
        days == 1L -> "Vence mañana"
        days > 1 -> "Vence en $days días"
        days == -1L -> "Venció ayer"
        else -> "Venció hace ${-days} días"
    }
}

fun OffsetDateTime.relativeLabel(now: OffsetDateTime = OffsetDateTime.now()): String {
    val elapsed = Duration.between(this, now)
    return when {
        elapsed.isNegative || elapsed.toMinutes() < 1 -> "Ahora"
        elapsed.toMinutes() < 60 -> "Hace ${elapsed.toMinutes()} min"
        elapsed.toHours() < 24 -> "Hace ${elapsed.toHours()} h"
        elapsed.toDays() < 7 -> "Hace ${elapsed.toDays()} d"
        else -> atZoneSameInstant(ZoneId.systemDefault()).toLocalDate().fieldLabel()
    }
}

fun Long.toCLPCurrency(): String =
    "$" + String.format(ChileLocale, "%,d", this).replace(',', '.')

fun String.toPriorityLabel(): String = when (this) {
    "high" -> "Alta"
    "medium" -> "Media"
    "low" -> "Baja"
    else -> "Sin prioridad"
}

fun Long.toColor() = Color(this)

// Estructura de pantallas -----------------------------------------------------

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(eyebrow.uppercase(ChileLocale), style = MaterialTheme.typography.labelSmall, color = GoldText)
        Spacer(Modifier.height(2.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = Brand,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.White,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, CardBorder)
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun Pill(text: String, content: Color, container: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun StatusPill(status: String, feminine: Boolean = true) {
    val style = statusStyle(status, feminine)
    Pill(style.label, style.content, style.container)
}

@Composable
fun Avatar(initials: String, color: Color = BrandLight, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials.ifBlank { "?" },
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp,
        )
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = BrandLight, container: Color = BrandSurface, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
fun SymbolBadge(symbol: String, color: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = color, fontSize = (size.value * 0.45f).sp)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Outlined.Schedule,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, contentPadding = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, tint = GoldText, container = GoldSurface)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

enum class BannerTone { Info, Error, Success }

@Composable
fun MessageBanner(
    message: String,
    tone: BannerTone = BannerTone.Error,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (content, container, icon) = when (tone) {
        BannerTone.Info -> Triple(Brand, BrandSurface, Icons.Outlined.Info)
        BannerTone.Error -> Triple(Danger, DangerSurface, Icons.Outlined.ErrorOutline)
        BannerTone.Success -> Triple(Success, SuccessSurface, Icons.Outlined.Check)
    }
    Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, color = content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = content) }
            }
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
fun DetailRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

// Formularios -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                enabled = enabled(option),
                label = { Text(label(option)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandContainer,
                    selectedLabelColor = Brand,
                    selectedLeadingIconColor = Brand,
                ),
            )
        }
    }
}

@Composable
fun FieldLabel(text: String, required: Boolean = false) {
    Text(
        if (required) "$text *" else text,
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary,
    )
}

@Composable
fun FieldError(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Danger)
}

/** Formulario a pantalla completa (patrón Material para formularios largos en móvil). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenForm(
    title: String,
    confirmLabel: String,
    canConfirm: Boolean,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            containerColor = AppBackground,
            topBar = {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !submitting) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cerrar sin guardar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                )
            },
            bottomBar = {
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Button(
                        onClick = onConfirm,
                        enabled = canConfirm && !submitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(16.dp)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Guardando…")
                        } else {
                            Text(confirmLabel)
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (error != null) MessageBanner(error, BannerTone.Error)
                content()
                Text("* Campo obligatorio", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null,
    isError: Boolean = false,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(if (required) "$label *" else label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.medium,
    )
}

/** Campo de solo lectura que abre un selector al tocarlo. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(icon, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
        Box(
            Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = "Cambiar $label", role = Role.Button, onClick = onClick),
        )
    }
    if (onClear != null && value.isNotBlank()) {
        TextButton(onClick = onClear) { Text("Quitar ${label.lowercase(ChileLocale).removeSuffix(" *")}") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    PickerField(
        label = label,
        value = value?.fieldLabel().orEmpty(),
        icon = Icons.Outlined.CalendarMonth,
        onClick = { open = true },
        modifier = modifier,
        onClear = if (clearable) ({ onValueChange(null) }) else null,
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        open = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    PickerField(
        label = label,
        value = value.toString(),
        icon = Icons.Outlined.Schedule,
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(LocalTime.of(state.hour, state.minute))
                        open = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun MemberRow(
    member: Member,
    selected: Boolean,
    multiSelect: Boolean,
    onToggle: () -> Unit,
    supporting: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (multiSelect) {
                    Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
                } else {
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onToggle)
                },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiSelect) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else {
            RadioButton(selected = selected, onClick = null)
        }
        Spacer(Modifier.width(12.dp))
        Avatar(member.initials, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(supporting ?: member.role, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

/** Lista de personas con búsqueda para selección simple o múltiple. */
@Composable
fun MemberPicker(
    members: List<Member>,
    selectedIds: Set<String>,
    multiSelect: Boolean,
    onChange: (Set<String>) -> Unit,
    emptyMessage: String,
) {
    var query by remember { mutableStateOf("") }
    val visible = members.filter { it.name.contains(query.trim(), ignoreCase = true) }
    if (members.size > 6) {
        TextInput(value = query, onValueChange = { query = it }, label = "Buscar persona")
    }
    if (members.isEmpty()) {
        Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = Color.White, border = BorderStroke(1.dp, CardBorder)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            visible.forEachIndexed { index, member ->
                if (index > 0) HorizontalDivider(color = CardBorder, modifier = Modifier.padding(start = 60.dp))
                MemberRow(
                    member = member,
                    selected = member.id in selectedIds,
                    multiSelect = multiSelect,
                    onToggle = {
                        onChange(
                            when {
                                !multiSelect -> setOf(member.id)
                                member.id in selectedIds -> selectedIds - member.id
                                else -> selectedIds + member.id
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Danger else Brand)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)

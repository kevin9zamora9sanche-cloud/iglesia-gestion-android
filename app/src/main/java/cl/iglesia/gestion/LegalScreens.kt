package cl.iglesia.gestion

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Datos del responsable y de las políticas. Los mismos valores se publican en
 * docs/ (GitHub Pages); al cambiar uno, actualiza también esas páginas y sube
 * [POLICY_VERSION] para volver a pedir el consentimiento.
 */
object LegalInfo {
    const val ORGANIZATION = "Centro Mundial de Adoración y Alabanza"
    // REEMPLAZAR antes de publicar: correo que atiende solicitudes de privacidad.
    const val CONTACT_EMAIL = "REEMPLAZAR@correo-de-la-iglesia.cl"
    // REEMPLAZAR antes de publicar: dirección o ciudad de la iglesia.
    const val ADDRESS = "REEMPLAZAR: dirección, comuna, Chile"
    const val WEB_BASE = "https://kevin9zamora9sanche-cloud.github.io/iglesia-gestion-android"
    const val PRIVACY_URL = "$WEB_BASE/privacidad.html"
    const val TERMS_URL = "$WEB_BASE/terminos.html"
    const val DELETION_URL = "$WEB_BASE/eliminar-cuenta.html"
    /** Fecha de la versión vigente de los Términos y la Política de Privacidad. */
    const val POLICY_VERSION = "2026-10-24"
    const val MINIMUM_AGE = 18
}

enum class LegalDocument(val title: String) {
    Privacy("Política de Privacidad"),
    Terms("Términos y Condiciones"),
    Licenses("Licencias de código abierto"),
}

private class LegalSection(val title: String, val body: String)

private val privacySections = listOf(
    LegalSection(
        "Responsable",
        "${LegalInfo.ORGANIZATION} (\"la Iglesia\"), ${LegalInfo.ADDRESS}. Contacto para privacidad: ${LegalInfo.CONTACT_EMAIL}.",
    ),
    LegalSection(
        "Qué datos tratamos",
        "Solo los necesarios para coordinar el trabajo interno de la Iglesia: nombre, correo electrónico, teléfono (opcional), " +
            "rol, ministerios a los que perteneces, actividades, tareas, comentarios y avisos asignados, los registros de reuniones " +
            "y archivos que tú u otros líderes adjunten, y un registro de seguridad de acciones críticas (quién hizo qué y cuándo).",
    ),
    LegalSection(
        "Qué datos no tratamos",
        "La aplicación no accede a tu ubicación, contactos, cámara, micrófono ni galería (solo al archivo que eliges adjuntar). " +
            "No usa publicidad, analítica, identificadores publicitarios, cookies ni tecnologías de seguimiento, y no procesa pagos.",
    ),
    LegalSection(
        "Datos sensibles",
        "Pertenecer a una iglesia o a sus ministerios puede revelar convicciones religiosas, que la ley chilena considera datos " +
            "sensibles. Por eso pedimos tu consentimiento expreso antes de usar la aplicación, y esos datos solo se usan dentro de la Iglesia.",
    ),
    LegalSection(
        "Para qué los usamos",
        "Para darte acceso a tu cuenta, organizar actividades, tareas, proyectos, ministerios y repertorios, enviarte avisos dentro de " +
            "la aplicación y proteger la cuenta (por ejemplo, el código de recuperación de contraseña). No vendemos ni cedemos tus datos " +
            "y no los usamos para publicidad ni para perfilarte.",
    ),
    LegalSection(
        "Quién puede verlos",
        "Según tu rol: el Pastor/Administrador y los líderes de tus ministerios ven los datos necesarios para su labor. Los datos se " +
            "alojan en Supabase, proveedor que actúa como encargado del tratamiento y puede almacenarlos fuera de Chile con medidas de " +
            "seguridad equivalentes. Solo los entregaremos a una autoridad cuando la ley lo exija.",
    ),
    LegalSection(
        "Seguridad",
        "Toda la comunicación viaja cifrada (HTTPS). El acceso se controla por rol en el servidor y la aplicación no copia tus datos " +
            "en respaldos en la nube del teléfono. La sesión queda guardada solo en tu dispositivo hasta que cierres sesión.",
    ),
    LegalSection(
        "Cuánto tiempo los conservamos",
        "Mientras tu cuenta esté activa. Si pides eliminarla, borramos o anonimizamos tus datos personales en un máximo de 30 días, " +
            "salvo lo que la ley obligue a conservar. El contenido que pertenece a la Iglesia (por ejemplo, actas) puede mantenerse sin tus datos de contacto.",
    ),
    LegalSection(
        "Tus derechos",
        "Puedes pedir acceso, rectificación, supresión, oposición, portabilidad y bloqueo de tus datos, y retirar tu consentimiento, " +
            "escribiendo a ${LegalInfo.CONTACT_EMAIL}. Responderemos en los plazos legales. Si no quedas conforme, puedes recurrir a la " +
            "autoridad de protección de datos competente.",
    ),
    LegalSection(
        "Menores de edad",
        "La aplicación es solo para personas de ${LegalInfo.MINIMUM_AGE} años o más. No creamos cuentas para menores; si detectamos " +
            "una, la eliminaremos.",
    ),
    LegalSection(
        "Eliminar tu cuenta",
        "Desde Más > Privacidad y datos > Solicitar eliminación de mi cuenta, o en ${LegalInfo.DELETION_URL}.",
    ),
    LegalSection(
        "Cambios",
        "Si cambiamos esta política te lo avisaremos en la aplicación y te pediremos aceptarla otra vez. Versión vigente: ${LegalInfo.POLICY_VERSION}.",
    ),
)

private val termsSections = listOf(
    LegalSection(
        "Qué es la aplicación",
        "Gestión Iglesia es una herramienta interna y gratuita de ${LegalInfo.ORGANIZATION} para coordinar actividades, tareas, " +
            "proyectos, ministerios y alabanza. No es un servicio público ni comercial.",
    ),
    LegalSection(
        "Sin costo",
        "La aplicación es gratuita: no tiene compras dentro de la app, suscripciones, publicidad ni cobros de ningún tipo. " +
            "Como no hay pagos, no aplican reembolsos.",
    ),
    LegalSection(
        "Cuentas",
        "Las cuentas las crea el Pastor/Administrador solo para miembros de la Iglesia mayores de ${LegalInfo.MINIMUM_AGE} años. " +
            "Eres responsable de mantener tu contraseña en secreto y de avisar si sospechas un uso indebido.",
    ),
    LegalSection(
        "Uso aceptable",
        "Usa la aplicación solo para fines de la Iglesia. No publiques contenido ofensivo, ilegal o datos de terceros sin necesidad, " +
            "ni intentes acceder a información que tu rol no permite.",
    ),
    LegalSection(
        "Archivos y derechos de autor",
        "Solo adjunta archivos (partituras, letras, presentaciones, documentos) que tengas derecho a compartir, por ser tuyos, de la " +
            "Iglesia, de dominio público o contar con licencia. Los archivos son para uso interno y no deben redistribuirse.",
    ),
    LegalSection(
        "Disponibilidad",
        "Procuramos que la aplicación funcione bien, pero puede tener interrupciones o errores. Conserva por otros medios la " +
            "información que sea indispensable.",
    ),
    LegalSection(
        "Suspensión y término",
        "La administración puede desactivar una cuenta que se use contra estos términos o cuando la persona deje de participar. " +
            "Puedes dejar de usar la aplicación y pedir la eliminación de tu cuenta en cualquier momento.",
    ),
    LegalSection(
        "Ley aplicable y contacto",
        "Estos términos se rigen por las leyes de Chile. Consultas: ${LegalInfo.CONTACT_EMAIL}. Versión vigente: ${LegalInfo.POLICY_VERSION}.",
    ),
)

private val licenseSections = listOf(
    LegalSection("Android Jetpack y Jetpack Compose", "Google LLC. Licencia Apache 2.0."),
    LegalSection("Material Icons", "Google LLC. Licencia Apache 2.0."),
    LegalSection("Kotlin y kotlinx", "JetBrains s.r.o. Licencia Apache 2.0."),
    LegalSection("Ktor", "JetBrains s.r.o. Licencia Apache 2.0."),
    LegalSection("supabase-kt", "Jan Tennert y colaboradores. Licencia MIT."),
    LegalSection("JSON-java (org.json)", "Sean Leary y colaboradores. Dominio público."),
    LegalSection(
        "Íconos e imágenes",
        "El ícono de la aplicación es un diseño propio. La aplicación no incluye fotografías ni imágenes de terceros.",
    ),
    LegalSection(
        "Texto de las licencias",
        "Apache 2.0: https://www.apache.org/licenses/LICENSE-2.0 — MIT: https://opensource.org/license/mit",
    ),
)

private fun LegalDocument.sections() = when (this) {
    LegalDocument.Privacy -> privacySections
    LegalDocument.Terms -> termsSections
    LegalDocument.Licenses -> licenseSections
}

// Consentimiento ---------------------------------------------------------------------

private const val CONSENT_PREFS = "legal_consent"

/** Versión de las políticas que este usuario aceptó en este dispositivo, o null. */
fun acceptedPolicyVersion(context: Context, userId: String): String? =
    context.getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE).getString(userId, null)

fun hasCurrentConsent(context: Context, userId: String) =
    acceptedPolicyVersion(context, userId) == LegalInfo.POLICY_VERSION

fun saveConsent(context: Context, userId: String) {
    context.getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE).edit()
        .putString(userId, LegalInfo.POLICY_VERSION)
        .putLong("$userId:at", System.currentTimeMillis())
        .apply()
}

/**
 * Se muestra tras iniciar sesión hasta aceptar la versión vigente. Las casillas
 * parten desmarcadas y rechazar es tan visible como aceptar.
 */
@Composable
fun ConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    var acceptsTerms by rememberSaveable { mutableStateOf(false) }
    var acceptsSensitive by rememberSaveable { mutableStateOf(false) }
    var isAdult by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf<LegalDocument?>(null) }

    reading?.let { document ->
        LegalDocumentScreen(document, onBack = { reading = null })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Antes de continuar", "Tu privacidad", "Versión ${LegalInfo.POLICY_VERSION}")
        Text(
            "Gestión Iglesia guarda tu nombre, correo, teléfono (si lo diste), tu rol, tus ministerios y las actividades y tareas " +
                "que te asignen, solo para coordinar el trabajo de ${LegalInfo.ORGANIZATION}. No usamos publicidad, analítica ni " +
                "cookies, y la aplicación no tiene cobros.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { reading = LegalDocument.Privacy }) { Text("Leer privacidad") }
            OutlinedButton(onClick = { reading = LegalDocument.Terms }) { Text("Leer términos") }
        }
        ConsentCheck(
            checked = acceptsTerms,
            onChange = { acceptsTerms = it },
            text = "Leí y acepto los Términos y Condiciones y la Política de Privacidad.",
        )
        ConsentCheck(
            checked = acceptsSensitive,
            onChange = { acceptsSensitive = it },
            text = "Autorizo que se registre mi participación en la Iglesia y sus ministerios, un dato que puede revelar mis convicciones religiosas.",
        )
        ConsentCheck(
            checked = isAdult,
            onChange = { isAdult = it },
            text = "Confirmo que tengo ${LegalInfo.MINIMUM_AGE} años o más.",
        )
        Text(
            "Puedes retirar tu consentimiento y pedir que eliminemos tu cuenta cuando quieras desde Más > Privacidad y datos.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Button(
            onClick = onAccept,
            enabled = acceptsTerms && acceptsSensitive && isAdult,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Aceptar y continuar") }
        OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("No acepto, cerrar sesión")
        }
    }
}

@Composable
private fun ConsentCheck(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El Row recibe el foco y el toque; la casilla solo muestra el estado.
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

// Documentos ---------------------------------------------------------------------------

@Composable
fun LegalDocumentScreen(document: LegalDocument, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackLink(onBack) }
        item {
            ScreenHeader(
                LegalInfo.ORGANIZATION,
                document.title,
                if (document == LegalDocument.Licenses) null else "Versión ${LegalInfo.POLICY_VERSION}",
            )
        }
        document.sections().forEach { section ->
            item {
                AppCard {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(section.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}

/** Enlaces legales al pie del inicio de sesión: deben poder leerse sin cuenta. */
@Composable
fun LegalLinks(onOpen: (LegalDocument) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = 480.dp), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { onOpen(LegalDocument.Privacy) }) { Text("Privacidad") }
        TextButton(onClick = { onOpen(LegalDocument.Terms) }) { Text("Términos") }
    }
}

// Privacidad y datos (menú Más) ----------------------------------------------------------

@Composable
fun PrivacyScreen(
    member: Member,
    onBack: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<LegalDocument?>(null) }
    var confirmDeletion by remember { mutableStateOf(false) }

    reading?.let { document ->
        LegalDocumentScreen(document, onBack = { reading = null })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackLink(onBack) }
        item { ScreenHeader("Tu cuenta", "Privacidad y datos") }
        item { LegalOption(Icons.Outlined.PrivacyTip, "Política de Privacidad", "Qué datos usamos y para qué") { reading = LegalDocument.Privacy } }
        item { LegalOption(Icons.Outlined.Gavel, "Términos y Condiciones", "Reglas de uso de la aplicación") { reading = LegalDocument.Terms } }
        item { LegalOption(Icons.Outlined.Code, "Licencias de código abierto", "Componentes de terceros que usa la app") { reading = LegalDocument.Licenses } }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Info)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Responsable de tus datos", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(LegalInfo.ORGANIZATION, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(LegalInfo.ADDRESS, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text(LegalInfo.CONTACT_EMAIL, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                acceptedPolicyVersion(context, member.id)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Aceptaste la versión $it de las políticas.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }
        item {
            LegalOption(
                Icons.Outlined.DeleteForever,
                "Solicitar eliminación de mi cuenta",
                "Borraremos tu cuenta y tus datos personales en un máximo de 30 días",
                tint = Danger,
            ) { confirmDeletion = true }
        }
    }

    if (confirmDeletion) {
        ConfirmDialog(
            title = "¿Eliminar tu cuenta?",
            message = "Se abrirá tu correo con la solicitud lista para enviar a ${LegalInfo.CONTACT_EMAIL}. " +
                "La administración eliminará tu cuenta y tus datos personales en un máximo de 30 días y te confirmará por correo.",
            confirmLabel = "Continuar",
            destructive = true,
            onDismiss = { confirmDeletion = false },
            onConfirm = {
                confirmDeletion = false
                if (!sendDeletionRequest(context, member)) {
                    onError("No hay una app de correo. Escribe a ${LegalInfo.CONTACT_EMAIL} para pedir la eliminación.")
                }
            },
        )
    }
}

@Composable
private fun LegalOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = BrandLight,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = tint, container = if (tint == Danger) DangerSurface else BrandSurface)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (tint == Danger) Danger else TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

/** Abre el correo con la solicitud prellenada. Devuelve false si no hay app de correo. */
private fun sendDeletionRequest(context: Context, member: Member): Boolean {
    val body = "Solicito eliminar mi cuenta de Gestión Iglesia y mis datos personales.\n\n" +
        "Nombre: ${member.name}\nCorreo de la cuenta: ${member.email}\nID de usuario: ${member.id}\n"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(LegalInfo.CONTACT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Solicitud de eliminación de cuenta")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

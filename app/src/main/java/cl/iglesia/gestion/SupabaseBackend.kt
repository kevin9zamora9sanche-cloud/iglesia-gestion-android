package cl.iglesia.gestion

import android.util.Base64
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.storage.Storage
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

object SupabaseBackend {
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
            !BuildConfig.SUPABASE_URL.contains("YOUR_PROJECT_REF") &&
            BuildConfig.SUPABASE_ANON_KEY.isPublicClientKey()

    val client by lazy {
        check(isConfigured) { "Configura Supabase en supabase.properties." }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Functions)
        }
    }

    private const val PROJECT_DESCRIPTION_MAX = 2000
    private const val MAX_RESPONSIBLE_LOOKUPS = 30

    suspend fun loadCurrentMember(): Member {
        val user = client.auth.retrieveUserForCurrentSession()
        val row = client.from("usuario")
            .select {
                filter {
                    eq("id", user.id)
                }
            }
            .decodeSingle<UsuarioRow>()

        // Con la cuenta inactiva RLS ya no devuelve nada, pero se valida por si cambia la política.
        StagingAccounts.accessProblem(row.estado)?.let { throw UserFacingException(it) }

        val memberships = client.from("miembro_ministerio")
            .select {
                filter {
                    eq("usuario_id", user.id)
                }
            }
            .decodeList<MiembroMinisterioRow>()
        val role = StagingMappings.roleFromStaging(row.rol)
        val displayName = StagingMappings.joinFullName(row.nombres, row.apellidos).ifBlank {
            user.email?.substringBefore("@")?.takeIf(String::isNotBlank) ?: "Usuario"
        }
        return Member(
            id = row.id,
            name = displayName,
            role = role,
            initials = displayName.initials(),
            email = user.email ?: row.correo.orEmpty(),
            active = true,
            phone = row.telefono.orEmpty(),
            ministryIds = memberships.map(MiembroMinisterioRow::ministerioId),
            ledMinistryIds = memberships
                .filter { it.esLider && role == Roles.MINISTRY_LEADER }
                .map(MiembroMinisterioRow::ministerioId),
            mustChangePassword = row.debeCambiarContrasena,
        )
    }

    suspend fun loadChurchSnapshot(currentMember: Member): ChurchSnapshot {
        val ministryRows = client.from("ministerio")
            .select { order("nombre", Order.ASCENDING) }
            .decodeList<MinisterioRow>()
        val membershipRows = client.from("miembro_ministerio")
            .select()
            .decodeList<MiembroMinisterioRow>()
        // RLS: el Pastor y los líderes de ministerio ven a todos; un líder normal solo su propia fila.
        val memberRows = client.from("usuario")
            .select { order("nombres", Order.ASCENDING) }
            .decodeList<UsuarioRow>()
        val membershipsByUser = membershipRows.groupBy(MiembroMinisterioRow::usuarioId)
        val members = memberRows.map { row ->
            val name = StagingMappings.joinFullName(row.nombres, row.apellidos).ifBlank {
                if (row.id == currentMember.id) currentMember.name else "Usuario"
            }
            val role = StagingMappings.roleFromStaging(row.rol)
            val memberships = membershipsByUser[row.id].orEmpty()
            Member(
                id = row.id,
                name = name,
                role = role,
                initials = name.initials(),
                email = if (row.id == currentMember.id) currentMember.email else row.correo.orEmpty(),
                active = StagingMappings.isActiveFromStaging(row.estado),
                phone = row.telefono.orEmpty(),
                ministryIds = memberships.map(MiembroMinisterioRow::ministerioId),
                ledMinistryIds = memberships
                    .filter { it.esLider && role == Roles.MINISTRY_LEADER }
                    .map(MiembroMinisterioRow::ministerioId),
            )
        }
        val allMinistries = ministryRows.mapIndexed { index, ministry ->
            Ministry(
                id = ministry.id,
                name = ministry.nombre,
                // Staging no guarda ícono ni color: se asignan por defecto en el cliente.
                icon = ministry.nombre.ministryIcon(),
                color = MINISTRY_COLORS[index % MINISTRY_COLORS.size],
                leaderIds = membershipRows
                    .filter { it.ministerioId == ministry.id && it.esLider }
                    .map(MiembroMinisterioRow::usuarioId),
                description = ministry.descripcion.orEmpty(),
                active = StagingMappings.isActiveFromStaging(ministry.estado),
                isWorship = ministry.esAlabanza,
            )
        }
        // SELECT de ministerio es abierto: los inactivos solo se muestran al Pastor.
        val (ministries, inactiveAll) = allMinistries.partition(Ministry::active)
        val inactiveMinistries = if (currentMember.isPastor) inactiveAll else emptyList()

        val zone = ZoneId.systemDefault()
        val actividadRows = client.from("actividad")
            .select()
            .decodeList<ActividadRow>()
        val responsablesByActivity = client.from("actividad_responsable")
            .select()
            .decodeList<ActividadResponsableRow>()
            .groupBy(ActividadResponsableRow::actividadId)
            .mapValues { (_, rows) -> rows.map(ActividadResponsableRow::usuarioId) }
        val cultoByActivity = client.from("detalle_culto")
            .select()
            .decodeList<DetalleCultoRow>()
            .associateBy(DetalleCultoRow::actividadId)
        val targetsByActivity = client.from("convocatoria")
            .select()
            .decodeList<ConvocatoriaRow>()
            .groupBy(ConvocatoriaRow::actividadId)
            .mapValues { (_, rows) ->
                rows.map { StagingActivities.Target(it.alcance, it.ministerioId, it.usuarioId) }
            }

        val tareaRows = client.from("tarea")
            .select()
            .decodeList<TareaRow>()
        val progressTasks = tareaRows.groupBy(TareaRow::actividadId)
        val commentsByTask = client.from("comentario_tarea")
            .select { order("created_at", Order.ASCENDING) }
            .decodeList<ComentarioTareaRow>()
            .groupBy(ComentarioTareaRow::tareaId)
        // El avance se calcula aquí con lo visible por RLS (staging no guarda una columna de avance).
        val progressByProject = actividadRows
            .filter { it.proyectoId != null }
            .groupBy { it.proyectoId!! }
            .mapValues { (_, rows) ->
                ProjectProgress.percent(
                    rows.map { row ->
                        ProjectProgress.Item(
                            StagingMappings.statusFromStaging(row.estado),
                            progressTasks[row.id].orEmpty().map { StagingMappings.statusFromStaging(it.estado) },
                        )
                    },
                )
            }
        val projects = client.from("proyecto")
            .select { order("fecha_cierre_estimada", Order.ASCENDING) }
            .decodeList<ProyectoRow>()
            .map { project ->
                val created = project.createdAt?.let(::parseTimestamp)?.toLocalDate()
                val start = project.fechaInicio?.let(LocalDate::parse) ?: created ?: LocalDate.now()
                ChurchProject(
                    id = project.id,
                    name = project.nombre,
                    objective = project.objetivo.orEmpty(),
                    description = project.descripcion.orEmpty(),
                    leaderId = project.responsableId ?: project.createdBy.orEmpty(),
                    startDate = start,
                    dueDate = project.fechaCierreEstimada?.let(LocalDate::parse) ?: start,
                    progress = progressByProject[project.id] ?: 0,
                    status = StagingMappings.statusFromStaging(project.estado),
                    budgetEstimated = project.presupuestoEstimado?.toLong(),
                    budgetExecuted = project.presupuestoEjecutado?.toLong(),
                )
            }

        val churchActivities = actividadRows.mapNotNull { row ->
            val inicio = parseTimestamp(row.inicio) ?: return@mapNotNull null
            val isMeeting = row.tipo == "REUNION_LIDERES"
            val responsables = responsablesByActivity[row.id].orEmpty()
            val culto = cultoByActivity[row.id]
            // Una reunión sin filas de convocatoria visibles se convoca al ministerio (si tiene).
            val targets = targetsByActivity[row.id].orEmpty().ifEmpty {
                listOfNotNull(row.ministerioId?.let { StagingActivities.Target("MINISTERIO", it, null) })
            }
            val ownMinistries = members.firstOrNull { it.id == currentMember.id }?.ministryIds ?: currentMember.ministryIds
            ChurchActivity(
                id = row.id,
                title = row.titulo,
                description = row.descripcion.orEmpty(),
                date = StagingActivities.localDate(inicio, zone),
                time = StagingActivities.timeLabel(inicio, zone),
                endTime = StagingActivities.timeLabel(row.fin?.let(::parseTimestamp), zone),
                ministryId = row.ministerioId,
                location = row.lugar.orEmpty(),
                status = StagingMappings.statusFromStaging(row.estado),
                kind = if (isMeeting) ItemKind.Meeting else ItemKind.Activity,
                category = StagingMappings.activityTypeFromStaging(row.tipo),
                classification = if (isMeeting && row.ministerioId == null) {
                    Classification.General
                } else {
                    StagingMappings.classificationFromStaging(row.clasificacion)
                },
                projectId = row.proyectoId,
                worshipLeaderId = culto?.directorAlabanzaId,
                preacherId = culto?.predicadorId,
                assigneeIds = if (isMeeting) {
                    targets.mapNotNull { it.userId }
                } else {
                    responsables
                },
                isAssignedToCurrentUser = if (isMeeting) {
                    StagingActivities.isConvoked(targets, currentMember.id, ownMinistries)
                } else {
                    currentMember.id in responsables
                },
            )
        }

        val activityRowById = actividadRows.associateBy(ActividadRow::id)
        val activityById = churchActivities.associateBy(ChurchActivity::id)
        // Una tarea cuya actividad no es visible se omite en vez de invalidar toda la carga.
        val churchTasks = tareaRows.mapNotNull { task ->
            val parent = activityById[task.actividadId] ?: return@mapNotNull null
            val parentStart = activityRowById[task.actividadId]?.inicio?.let(::parseTimestamp)
                ?: return@mapNotNull null
            val dueAt = task.fechaLimite?.let(::parseTimestamp)
            val schedule = StagingTasks.schedule(dueAt, parentStart, zone)
            ChurchActivity(
                id = task.id,
                title = task.titulo,
                description = task.instrucciones.orEmpty(),
                date = schedule.date,
                time = schedule.time,
                endTime = schedule.time,
                ministryId = parent.ministryId,
                location = parent.location,
                status = StagingMappings.statusFromStaging(task.estado),
                kind = ItemKind.Task,
                classification = parent.classification,
                projectId = parent.projectId,
                assigneeIds = listOfNotNull(task.responsableId),
                isAssignedToCurrentUser = task.responsableId == currentMember.id,
                parentActivityId = parent.id,
                parentActivityTitle = parent.title,
                priority = StagingMappings.priorityFromStaging(task.prioridad),
                taskComments = commentsByTask[task.id].orEmpty().map {
                    TaskComment(it.id, it.autorId, it.texto, it.createdAt?.let(::parseTimestamp))
                },
                dueAt = dueAt,
            )
        }

        // `usuario` oculta a otras personas a un Líder normal: sus nombres se piden por RPC.
        val knownIds = members.mapTo(mutableSetOf(), Member::id)
        val hiddenMembers = if (currentMember.role == Roles.LEADER) {
            val horizon = LocalDate.now().minusDays(7)
            churchActivities
                .filter { it.kind == ItemKind.Activity && !it.date.isBefore(horizon) && it.assigneeIds.any { id -> id !in knownIds } }
                .take(MAX_RESPONSIBLE_LOOKUPS)
                .flatMap { activity ->
                    try {
                        listResponsibles(activity.id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("SupabaseBackend", "Responsables no disponibles", error)
                        emptyList()
                    }
                }
                .distinctBy(Member::id)
                .filter { it.id !in knownIds }
        } else {
            emptyList()
        }

        val loadedArchivos = loadArchivos()
        // RLS: solo el Pastor, Alabanza y quienes participan en cultos ven canciones y repertorio.
        val songFiles = loadedArchivos.filter { it.cancionId != null }.groupBy { it.cancionId }
        val songs = client.from("cancion")
            .select { order("titulo", Order.ASCENDING) }
            .decodeList<CancionRow>()
            .map {
                WorshipSong(
                    id = it.id,
                    title = it.titulo,
                    artist = it.artista.orEmpty(),
                    defaultKey = it.tonalidad.orEmpty(),
                    referenceUrl = it.enlaceVersion.orEmpty(),
                    files = songFiles[it.id].orEmpty().map { file -> file.toRecordFile() },
                )
            }
        val entriesByRepertoire = client.from("repertorio_cancion")
            .select()
            .decodeList<RepertorioCancionRow>()
            .groupBy(RepertorioCancionRow::repertorioId)
        val worshipSets = client.from("repertorio")
            .select { order("updated_at", Order.DESCENDING) }
            .decodeList<RepertorioRow>()
            .map { set ->
                WorshipSet(
                    id = set.id,
                    updatedAt = parseTimestamp(set.updatedAt),
                    songs = entriesByRepertoire[set.id].orEmpty()
                        .sortedBy(RepertorioCancionRow::orden)
                        .map {
                            WorshipSetSong(
                                songId = it.cancionId,
                                position = it.orden,
                                key = it.tonalidad.orEmpty(),
                                leadId = it.directorId,
                                versionUrl = it.enlaceVersion.orEmpty(),
                            )
                        },
                )
            }

        val notifications = client.from("notifications")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList<SupabaseNotification>()
            .map {
                ChurchNotification(
                    id = it.id,
                    message = it.message,
                    type = it.type,
                    createdAt = parseTimestamp(it.createdAt),
                    read = it.read,
                )
            }

        val meetingRecords = loadMeetingRecords(loadedArchivos)

        return ChurchSnapshot(
            members = members + hiddenMembers,
            ministries = ministries,
            inactiveMinistries = inactiveMinistries,
            activities = (churchActivities + churchTasks).sortedWith(
                compareBy(ChurchActivity::date, ChurchActivity::time),
            ),
            projects = projects,
            songs = songs,
            worshipSets = worshipSets,
            notifications = notifications,
            meetingRecords = meetingRecords,
        )
    }

    private suspend fun loadArchivos(): List<ArchivoRow> =
        client.from("archivo")
            .select { order("created_at", Order.ASCENDING) }
            .decodeList<ArchivoRow>()

    private fun ArchivoRow.toRecordFile() =
        RecordFile(id, nombre, StagingFiles.mimeFromType(tipo), tamanoBytes.toLong(), ruta)

    private suspend fun loadMeetingRecords(archivos: List<ArchivoRow>): List<MeetingRecord> {
        val zone = ZoneId.systemDefault()
        val visibilityByRecord = client.from("visibilidad_reunion")
            .select()
            .decodeList<VisibilidadReunionRow>()
            .groupBy(VisibilidadReunionRow::reunionId)
        val filesByRecord = archivos.filter { it.reunionId != null }.groupBy { it.reunionId }
        return client.from("reunion")
            .select { order("fecha", Order.DESCENDING) }
            .decodeList<ReunionRow>()
            .map { record ->
                val visibility = StagingRecords.resolveVisibility(
                    visibilityByRecord[record.id].orEmpty().map {
                        StagingRecords.VisibilityRow(it.alcance, it.ministerioId, it.usuarioId)
                    },
                )
                MeetingRecord(
                    id = record.id,
                    meetingId = record.actividadId,
                    title = record.nombre,
                    date = parseTimestamp(record.fecha)?.let { StagingRecords.timestampToDate(it, zone) } ?: LocalDate.now(),
                    summary = record.resumen.orEmpty(),
                    visibility = visibility.visibility,
                    ministryId = visibility.ministryId,
                    viewerIds = visibility.viewerIds,
                    files = filesByRecord[record.id].orEmpty().map { it.toRecordFile() },
                )
            }
    }

    /**
     * Crea la reunión, su visibilidad y sube sus archivos (solo Pastor, por RLS). Si un archivo
     * falla, el registro queda guardado y se informa exactamente qué archivos no se adjuntaron.
     */
    suspend fun createMeetingRecord(currentMember: Member, draft: MeetingRecordDraft, files: List<PickedFile>) {
        require(currentMember.isPastor) { "Solo el Pastor/Administrador registra reuniones." }
        val recordId = UUID.randomUUID().toString()
        try {
            client.from("reunion").insert(
                ReunionInsert(
                    id = recordId,
                    actividadId = draft.meetingId,
                    nombre = draft.title.trim(),
                    fecha = StagingRecords.dateToTimestamp(draft.date, ZoneId.systemDefault()).toString(),
                    resumen = draft.summary.trim().ifBlank { null },
                    registradoPor = currentMember.id,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudo crear la reunión", error)
            throw UserFacingException(
                "No se pudo guardar el registro." +
                    if (draft.meetingId != null) " Esa reunión podría tener ya un registro." else "",
            )
        }
        val rows = StagingRecords.visibilityRows(draft.visibility, draft.ministryId, draft.viewerIds)
        if (rows.isNotEmpty()) {
            try {
                client.from("visibilidad_reunion").insert(
                    rows.map { VisibilidadReunionInsert(recordId, it.alcance, it.ministryId, it.userId) },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("SupabaseBackend", "No se pudo guardar la visibilidad", error)
                // Sin filas de visibilidad solo el Pastor lo vería: se deshace para no dejarlo a medias.
                runCatching { client.from("reunion").delete { filter { eq("id", recordId) } } }
                throw UserFacingException("No se pudo guardar quién puede ver el registro, por lo que no se creó.")
            }
        }
        val failed = files.filter { file ->
            try {
                uploadRecordFile(currentMember, recordId, file)
                false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("SupabaseBackend", "No se pudo adjuntar ${file.name}", error)
                true
            }
        }
        if (failed.isNotEmpty()) {
            throw PartialSaveException(
                "El registro se guardó, pero no se pudieron adjuntar: ${failed.joinToString { it.name }}. " +
                    "Puedes volver a adjuntarlos desde el registro.",
            )
        }
    }

    suspend fun uploadRecordFile(currentMember: Member, recordId: String, file: PickedFile) =
        uploadArchivo(currentMember, "reunion", recordId, file)

    suspend fun deleteRecordFile(file: RecordFile) = deleteArchivo(file)

    suspend fun deleteMeetingRecord(record: MeetingRecord) {
        val deleted = try {
            // Las filas de archivo y visibilidad se eliminan en cascada.
            client.from("reunion").delete {
                select()
                filter { eq("id", record.id) }
            }.decodeList<IdRow>()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudo eliminar el registro", error)
            throw UserFacingException("No se pudo eliminar el registro.")
        }
        if (deleted.isEmpty()) throw UserFacingException("Solo el Pastor/Administrador puede eliminar registros.")
        removeStorageObjects(record.files.map(RecordFile::objectPath))
    }

    /**
     * Sube un archivo al bucket `archivos` y registra su fila en `archivo`
     * (`<owner_type>/<owner_id>/<archivo>`). Si la fila falla, el objeto se elimina.
     */
    private suspend fun uploadArchivo(currentMember: Member, ownerType: String, ownerId: String, file: PickedFile) {
        val upload = StagingFiles.prepareUpload(
            ownerType = ownerType,
            ownerId = ownerId,
            fileName = file.name,
            mime = file.contentType,
            sizeBytes = file.bytes.size.toLong(),
            uniqueId = UUID.randomUUID().toString().take(8),
        )
        val bucket = client.storage.from(StagingFiles.BUCKET)
        bucket.upload(upload.path, file.bytes, upsert = false)
        try {
            client.from("archivo").insert(
                ArchivoInsert(
                    nombre = upload.name,
                    ruta = upload.path,
                    tipo = upload.type,
                    tamanoBytes = upload.sizeBytes,
                    subidoPor = currentMember.id,
                    cancionId = ownerId.takeIf { ownerType == "cancion" },
                    reunionId = ownerId.takeIf { ownerType == "reunion" },
                    actividadId = ownerId.takeIf { ownerType == "actividad" },
                    ministerioId = ownerId.takeIf { ownerType == "ministerio" },
                ),
            )
        } catch (error: Exception) {
            // Sin fila asociada el objeto quedaría huérfano: se elimina.
            runCatching { bucket.delete(upload.path) }
            throw error
        }
    }

    /** Elimina la fila de `archivo` y luego su objeto; RLS no devuelve filas si no hay permiso. */
    private suspend fun deleteArchivo(file: RecordFile) {
        val deleted = client.from("archivo").delete {
            select()
            filter { eq("id", file.id) }
        }.decodeList<IdRow>()
        if (deleted.isEmpty()) throw UserFacingException("No tienes permiso para eliminar este archivo.")
        removeStorageObjects(listOf(file.objectPath))
    }

    /** Un objeto huérfano no afecta a nadie (sin fila no se puede leer), así que un fallo solo se registra. */
    private suspend fun removeStorageObjects(paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            client.storage.from(StagingFiles.BUCKET).delete(paths)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudieron eliminar objetos de Storage", error)
        }
    }

    /** Enlace temporal de descarga; Storage valida los permisos del propietario del archivo. */
    suspend fun signedFileUrl(file: RecordFile): String {
        val url = client.storage.from(StagingFiles.BUCKET).createSignedUrl(file.objectPath, 5.minutes)
        return if (url.startsWith("http")) url else BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/" + url.trimStart('/')
    }

    suspend fun createProject(currentMember: Member, draft: ProjectDraft) {
        require(currentMember.isPastor) {
            "Solo el Pastor/Administrador puede crear proyectos."
        }
        require(draft.description.trim().length <= PROJECT_DESCRIPTION_MAX) {
            "La descripción no puede superar $PROJECT_DESCRIPTION_MAX caracteres."
        }
        client.from("proyecto").insert(
            ProyectoInsert(
                nombre = draft.name.trim(),
                objetivo = draft.objective.trim(),
                descripcion = draft.description.trim().ifBlank { null },
                responsableId = draft.leaderId,
                fechaInicio = draft.startDate.toString(),
                fechaCierreEstimada = draft.dueDate.toString(),
                presupuestoEstimado = draft.budgetEstimated,
                presupuestoEjecutado = draft.budgetExecuted,
                estado = StagingMappings.statusToStaging(draft.status),
                createdBy = currentMember.id,
            ),
        )
    }

    suspend fun updateProject(currentMember: Member, draft: ProjectDraft) {
        require(currentMember.isPastor) {
            "Solo el Pastor/Administrador puede editar proyectos."
        }
        val projectId = requireNotNull(draft.id) { "El proyecto a editar no es válido." }
        require(draft.status in WORK_STATUSES) { "El estado seleccionado no es válido." }
        require(draft.description.trim().length <= PROJECT_DESCRIPTION_MAX) {
            "La descripción no puede superar $PROJECT_DESCRIPTION_MAX caracteres."
        }
        val updated = client.from("proyecto").update(
            ProyectoUpdate(
                nombre = draft.name.trim(),
                objetivo = draft.objective.trim(),
                descripcion = draft.description.trim().ifBlank { null },
                responsableId = draft.leaderId,
                fechaInicio = draft.startDate.toString(),
                fechaCierreEstimada = draft.dueDate.toString(),
                presupuestoEstimado = draft.budgetEstimated,
                presupuestoEjecutado = draft.budgetExecuted,
                estado = StagingMappings.statusToStaging(draft.status),
            ),
        ) {
            select()
            filter {
                eq("id", projectId)
            }
        }.decodeList<ProyectoRow>()
        if (updated.isEmpty()) throw UserFacingException("No se pudo actualizar el proyecto: sin permiso.")
    }

    suspend fun createActivity(currentMember: Member, draft: ActivityDraft) {
        require(draft.assigneeIds.isNotEmpty()) { "Selecciona al menos un responsable." }
        val ministryId = draft.ministryId.takeIf { draft.classification == Classification.Ministry }
        val projectId = draft.projectId.takeIf { draft.classification == Classification.Project }
        require(currentMember.isPastor || currentMember.canManageMinistry(ministryId)) {
            "Solo puedes crear actividades dentro de los ministerios que lideras."
        }
        val activityId = callCrearActividad(
            title = draft.title,
            tipo = StagingMappings.activityTypeToStaging(draft.category),
            classification = draft.classification,
            projectId = projectId,
            ministryId = ministryId,
            description = draft.description,
            inicio = StagingActivities.combine(draft.date, draft.startTime, ZoneId.systemDefault()),
            location = draft.location,
            responsibles = draft.assigneeIds.distinct(),
        )
        setActivityEnd(activityId, StagingActivities.combine(draft.date, draft.endTime, ZoneId.systemDefault()))
        // `detalle_culto` solo lo escribe el Pastor (RLS).
        val culto = StagingActivities.cultoDetail(draft.preacherId, draft.worshipLeaderId)
        if (draft.category == ActivityTypes.CULTO && currentMember.isPastor && culto != null) {
            try {
                client.from("detalle_culto").insert(
                    DetalleCultoInsert(activityId, culto.preacherId, culto.worshipLeaderId),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw PartialSaveException("La actividad se creó, pero no se guardaron el predicador ni el director de alabanza.")
            }
        }
    }

    /** Una reunión es una actividad REUNION_LIDERES más filas de `convocatoria`. */
    suspend fun createMeeting(currentMember: Member, draft: MeetingDraft) {
        require(currentMember.isPastor || currentMember.canManageMinistry(draft.ministryId)) {
            "Solo puedes convocar reuniones de los ministerios que lideras."
        }
        val zone = ZoneId.systemDefault()
        val meetingId = callCrearActividad(
            title = draft.title,
            tipo = "REUNION_LIDERES",
            classification = StagingActivities.meetingClassification(draft.ministryId),
            projectId = null,
            ministryId = draft.ministryId,
            description = draft.purpose,
            inicio = StagingActivities.combine(draft.date, draft.startTime, zone),
            location = draft.location,
            responsibles = emptyList(),
        )
        setActivityEnd(meetingId, StagingActivities.combine(draft.date, draft.endTime, zone))
        // `convocatoria` solo la escribe el Pastor: la reunión de un líder queda convocada a su ministerio.
        if (currentMember.isPastor) {
            try {
                client.from("convocatoria").insert(
                    StagingActivities.convocatoriaTargets(draft.ministryId, draft.participantIds).map {
                        ConvocatoriaInsert(meetingId, it.alcance, it.ministryId, it.userId)
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw PartialSaveException("La reunión se creó, pero no se pudo guardar la convocatoria.")
            }
        }
    }

    private suspend fun callCrearActividad(
        title: String,
        tipo: String,
        classification: Classification,
        projectId: String?,
        ministryId: String?,
        description: String,
        inicio: OffsetDateTime,
        location: String,
        responsibles: List<String>,
    ): String = client.postgrest.rpc(
        function = "crear_actividad",
        parameters = buildJsonObject {
            put("p_titulo", title.trim())
            put("p_tipo", tipo)
            put("p_clasificacion", StagingMappings.classificationToStaging(classification))
            put("p_proyecto_id", projectId)
            put("p_ministerio_id", ministryId)
            put("p_descripcion", description.trim())
            put("p_inicio", inicio.toString())
            put("p_lugar", location.trim())
            put("p_estado", "POR_HACER")
            putJsonArray("p_responsables") { responsibles.forEach { add(it) } }
        },
    ).decodeAs<String>()

    /** `crear_actividad` no recibe la hora de término: se guarda con un UPDATE posterior. */
    private suspend fun setActivityEnd(activityId: String, end: OffsetDateTime) {
        try {
            val updated = client.from("actividad").update(ActividadFinUpdate(end.toString())) {
                select()
                filter { eq("id", activityId) }
            }.decodeList<ActividadEstadoRow>()
            if (updated.isEmpty()) throw IllegalStateException("fin no actualizado")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw PartialSaveException("Se creó, pero no se pudo guardar la hora de término.")
        }
    }

    /** Nombres de los responsables vía RPC (usuario oculta a otras personas a un Líder normal). */
    suspend fun listResponsibles(activityId: String): List<Member> =
        client.postgrest.rpc(
            function = "listar_responsables_actividad",
            parameters = buildJsonObject { put("p_actividad_id", activityId) },
        ).decodeList<ResponsableNombreRow>().map { row ->
            val name = StagingMappings.joinFullName(row.nombres, row.apellidos).ifBlank { "Usuario" }
            Member(
                id = row.usuarioId,
                name = name,
                role = Roles.LEADER,
                initials = name.initials(),
                email = "",
                // Solo para mostrar el nombre: nunca aparece como persona elegible.
                active = false,
            )
        }

    suspend fun updateActivityStatus(activityId: String, status: String) {
        require(status in WORK_STATUSES) { "El estado seleccionado no es válido." }
        val updated = try {
            client.from("actividad").update(ActividadEstadoUpdate(StagingMappings.statusToStaging(status))) {
                select()
                filter { eq("id", activityId) }
            }.decodeList<ActividadEstadoRow>()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw UserFacingException(
                "No se pudo cambiar el estado. Reabrir una actividad completada solo lo hace el Pastor o el líder del ministerio.",
            )
        }
        // RLS afecta 0 filas sin error cuando no hay permiso.
        if (updated.isEmpty()) throw UserFacingException("No tienes permiso para cambiar el estado de esta actividad.")
    }

    suspend fun createTask(currentMember: Member, draft: TaskDraft) {
        require(currentMember.isPastor || currentMember.isMinistryLeader) {
            "Solo el Pastor/Administrador o el Líder de ministerio puede asignar tareas."
        }
        val created = client.from("tarea").insert(
            TareaInsert(
                actividadId = draft.activityId,
                titulo = draft.title.trim(),
                instrucciones = draft.instructions.trim().ifBlank { null },
                responsableId = draft.assigneeId,
                fechaLimite = draft.dueAt?.toString(),
                prioridad = StagingMappings.priorityToStaging(draft.priority),
                estado = "POR_HACER",
                createdBy = currentMember.id,
            ),
        ) {
            select()
        }.decodeSingle<IdRow>()
        // Los comentarios ahora son filas propias: el texto inicial se guarda como el primero.
        StagingTasks.commentOrNull(draft.comments)?.let { text ->
            try {
                addTaskComment(currentMember, created.id, text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw PartialSaveException("La tarea se creó, pero no se pudo guardar el comentario.")
            }
        }
    }

    suspend fun updateTaskStatus(taskId: String, status: String) {
        require(status in WORK_STATUSES) { "El estado seleccionado no es válido." }
        val updated = try {
            client.from("tarea").update(TareaEstadoUpdate(StagingMappings.statusToStaging(status))) {
                select()
                filter { eq("id", taskId) }
            }.decodeList<IdRow>()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw UserFacingException(
                "No se pudo cambiar el estado. Reabrir una tarea completada solo lo hace el Pastor o el líder del ministerio.",
            )
        }
        // RLS afecta 0 filas sin error cuando no hay permiso.
        if (updated.isEmpty()) throw UserFacingException("No tienes permiso para cambiar el estado de esta tarea.")
    }

    suspend fun addTaskComment(currentMember: Member, taskId: String, text: String) {
        val clean = requireNotNull(StagingTasks.commentOrNull(text)) { "El comentario no puede estar vacío." }
        client.from("comentario_tarea").insert(ComentarioTareaInsert(taskId, currentMember.id, clean))
    }

    suspend fun markNotificationRead(notificationId: String) {
        client.from("notifications").update(NotificationReadUpdate(read = true)) {
            filter {
                eq("id", notificationId)
            }
        }
    }

    suspend fun markAllNotificationsRead(currentMember: Member) {
        client.from("notifications").update(NotificationReadUpdate(read = true)) {
            filter {
                eq("user_id", currentMember.id)
                eq("read", false)
            }
        }
    }

    suspend fun createWorshipSong(
        currentMember: Member,
        draft: WorshipSongDraft,
        files: List<PickedFile> = emptyList(),
    ) {
        val songId = UUID.randomUUID().toString()
        val fields = StagingWorship.songFields(draft)
        try {
            client.from("cancion").insert(
                CancionInsert(
                    id = songId,
                    titulo = fields.title,
                    artista = fields.artist,
                    tonalidad = fields.key,
                    enlaceVersion = fields.versionUrl,
                    registradoPor = currentMember.id,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudo crear la canción", error)
            throw UserFacingException("No se pudo guardar la canción. Solo el Pastor o el líder de Alabanza puede agregarlas.")
        }
        val failed = files.filter { file ->
            try {
                uploadSongFile(currentMember, songId, file)
                false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("SupabaseBackend", "No se pudo adjuntar ${file.name}", error)
                true
            }
        }
        if (failed.isNotEmpty()) {
            throw PartialSaveException(
                "La canción se guardó, pero no se pudieron adjuntar: ${failed.joinToString { it.name }}. " +
                    "Puedes volver a adjuntarlos desde el catálogo.",
            )
        }
    }

    suspend fun updateWorshipSong(songId: String, draft: WorshipSongDraft) {
        val fields = StagingWorship.songFields(draft)
        val updated = try {
            client.from("cancion").update(
                CancionUpdate(
                    titulo = fields.title,
                    artista = fields.artist,
                    tonalidad = fields.key,
                    enlaceVersion = fields.versionUrl,
                ),
            ) {
                select()
                filter { eq("id", songId) }
            }.decodeList<IdRow>()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudo actualizar la canción", error)
            throw UserFacingException("No se pudo guardar la canción.")
        }
        // RLS afecta 0 filas sin error cuando no hay permiso.
        if (updated.isEmpty()) throw UserFacingException("No tienes permiso para editar canciones.")
    }

    /**
     * Elimina una canción y sus archivos. La base elimina en cascada su lugar en los
     * repertorios (`repertorio_cancion`) y las filas de `archivo`; luego se borran los objetos.
     */
    suspend fun deleteWorshipSong(song: WorshipSong) {
        val deleted = try {
            client.from("cancion").delete {
                select()
                filter { eq("id", song.id) }
            }.decodeList<IdRow>()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudo eliminar la canción", error)
            throw UserFacingException("No se pudo eliminar la canción.")
        }
        if (deleted.isEmpty()) throw UserFacingException("No tienes permiso para eliminar canciones.")
        removeStorageObjects(song.files.map(RecordFile::objectPath))
    }

    suspend fun uploadSongFile(currentMember: Member, songId: String, file: PickedFile) =
        uploadArchivo(currentMember, "cancion", songId, file)

    suspend fun deleteSongFile(file: RecordFile) = deleteArchivo(file)

    /**
     * Reemplaza las canciones del repertorio vigente (o crea el repertorio si no existe).
     * No hay RPC: se borran y se vuelven a insertar las filas; si la inserción falla se
     * restauran las anteriores para no dejar el repertorio vacío.
     */
    suspend fun saveRepertoire(currentMember: Member, existing: WorshipSet?, songs: List<WorshipSetSong>) {
        val repertoireId = existing?.id ?: UUID.randomUUID().toString()
        val now = OffsetDateTime.now().toString()
        try {
            if (existing == null) {
                client.from("repertorio").insert(RepertorioInsert(repertoireId, currentMember.id, now))
            } else {
                val updated = client.from("repertorio").update(RepertorioUpdate(currentMember.id, now)) {
                    select()
                    filter { eq("id", repertoireId) }
                }.decodeList<IdRow>()
                if (updated.isEmpty()) throw UserFacingException("No tienes permiso para editar el repertorio.")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: UserFacingException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudo guardar el repertorio", error)
            throw UserFacingException("No se pudo guardar el repertorio. Solo el Pastor o el líder de Alabanza puede editarlo.")
        }
        val rows = StagingWorship.repertoireRows(repertoireId, songs)
        val previous = StagingWorship.repertoireRows(repertoireId, existing?.songs.orEmpty())
        client.from("repertorio_cancion").delete {
            filter { eq("repertorio_id", repertoireId) }
        }
        if (rows.isEmpty()) return
        try {
            client.from("repertorio_cancion").insert(rows.map { RepertorioCancionInsert(it) })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudieron guardar las canciones del repertorio", error)
            val restored = previous.isEmpty() || runCatching {
                client.from("repertorio_cancion").insert(previous.map { RepertorioCancionInsert(it) })
            }.isSuccess
            throw UserFacingException(
                if (restored) "No se pudo guardar el repertorio; se conservó la versión anterior."
                else "El repertorio quedó incompleto. Vuelve a guardarlo.",
            )
        }
    }

    suspend fun createMinistry(currentMember: Member, draft: MinistryDraft) {
        require(currentMember.isPastor) { "Solo el Pastor/Administrador puede crear ministerios." }
        client.from("ministerio").insert(
            MinisterioInsert(nombre = draft.name.trim(), descripcion = draft.description.trim()),
        )
    }

    suspend fun updateMinistry(currentMember: Member, ministryId: String, draft: MinistryDraft) {
        require(currentMember.isPastor) { "Solo el Pastor/Administrador puede editar ministerios." }
        client.from("ministerio").update(MinisterioUpdate(draft.name.trim(), draft.description.trim())) {
            filter { eq("id", ministryId) }
        }
    }

    /** Desactiva o reactiva un ministerio; staging no permite borrarlos. Integrantes y registros se conservan. */
    suspend fun setMinistryActive(currentMember: Member, ministryId: String, active: Boolean) {
        require(currentMember.isPastor) { "Solo el Pastor/Administrador puede activar o desactivar ministerios." }
        client.from("ministerio").update(MinisterioEstadoUpdate(StagingMappings.userStatusToStaging(active))) {
            filter { eq("id", ministryId) }
        }
    }

    suspend fun addMembership(ministryId: String, userId: String, isLeader: Boolean) {
        client.from("miembro_ministerio").insert(
            MiembroMinisterioRow(ministerioId = ministryId, usuarioId = userId, esLider = isLeader),
        )
    }

    suspend fun setMembershipLeader(ministryId: String, userId: String, isLeader: Boolean) {
        client.from("miembro_ministerio").update(MiembroLiderUpdate(isLeader)) {
            filter {
                eq("ministerio_id", ministryId)
                eq("usuario_id", userId)
            }
        }
    }

    suspend fun removeMembership(ministryId: String, userId: String) {
        client.from("miembro_ministerio").delete {
            filter {
                eq("ministerio_id", ministryId)
                eq("usuario_id", userId)
            }
        }
    }

    /** Cambia los responsables de una actividad agregando y quitando solo la diferencia. */
    suspend fun setActivityAssignees(currentMember: Member, activity: ChurchActivity, desiredIds: Set<String>) {
        require(desiredIds.isNotEmpty()) { "La actividad debe tener al menos un responsable." }
        val current = activity.assigneeIds.toSet()
        val toAdd = desiredIds - current
        val toRemove = current - desiredIds
        if (toAdd.isNotEmpty()) {
            client.from("actividad_responsable").insert(
                toAdd.map { ActividadResponsableInsert(activity.id, it) },
            )
        }
        toRemove.forEach { userId ->
            client.from("actividad_responsable").delete {
                filter {
                    eq("actividad_id", activity.id)
                    eq("usuario_id", userId)
                }
            }
        }
    }

    suspend fun reassignTask(taskId: String, assigneeId: String) {
        val updated = client.from("tarea").update(TareaResponsableUpdate(assigneeId)) {
            select()
            filter { eq("id", taskId) }
        }.decodeList<IdRow>()
        if (updated.isEmpty()) throw UserFacingException("No tienes permiso para reasignar esta tarea.")
    }

    /**
     * Crea la cuenta con la Edge Function `create-user` (solo Pastor activo). El servidor
     * genera la contraseña temporal y la devuelve una única vez.
     */
    suspend fun createUserAccount(draft: NewUserDraft): CreatedAccount {
        StagingAccounts.createUserProblem(draft.fullName, draft.email)?.let { throw UserFacingException(it) }
        val (nombres, apellidos) = StagingMappings.splitFullName(draft.fullName)
        val body = buildJsonObject {
            put("nombres", nombres)
            put("apellidos", apellidos)
            put("correo", draft.email.trim())
            putJsonArray("ministerioIds") { draft.memberships.keys.forEach { add(it) } }
        }
        val response = try {
            client.functions.invoke("create-user", body)
        } catch (error: CancellationException) {
            throw error
        } catch (error: io.github.jan.supabase.exceptions.RestException) {
            val raw = error.message.orEmpty() + " " + error.description
            val serverMessage = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
            throw UserFacingException(StagingAccounts.createUserErrorMessage(error.statusCode, serverMessage))
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "create-user no disponible", error)
            throw UserFacingException("No se pudo contactar el servicio de cuentas. Revisa tu conexión e inténtalo de nuevo.")
        }
        val text = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        if (!response.status.isSuccess()) {
            throw UserFacingException(
                StagingAccounts.createUserErrorMessage(response.status.value, json["error"]?.jsonPrimitive?.content),
            )
        }
        val id = json["id"]?.jsonPrimitive?.content
        val password = json["password"]?.jsonPrimitive?.content
        if (id.isNullOrBlank() || password.isNullOrBlank()) {
            throw UserFacingException("La cuenta se creó, pero el servidor no devolvió la contraseña temporal. Pide ayuda al equipo técnico.")
        }
        return CreatedAccount(id, json["correo"]?.jsonPrimitive?.content ?: draft.email.trim(), password)
    }

    /** `create-user` solo fija nombre, correo y ministerios; rol, teléfono y liderazgo se aplican aquí. */
    suspend fun applyNewUserDetails(userId: String, draft: NewUserDraft) {
        val phone = draft.phone.trim()
        val role = StagingMappings.roleToStaging(draft.role)
        if (phone.isNotEmpty() || role != "LIDER_NORMAL") {
            client.from("usuario").update(UsuarioDetalleUpdate(rol = role, telefono = phone.ifBlank { null })) {
                filter { eq("id", userId) }
            }
        }
        draft.memberships.filterValues { it }.keys.forEach { setMembershipLeader(it, userId, true) }
    }

    suspend fun sendPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email.trim())
    }

    /**
     * Recuperación con el código del correo: valida el código (inicia la sesión) y
     * guarda la contraseña nueva. Requiere que la plantilla "Reset Password" de
     * Supabase incluya {{ .Token }}.
     */
    suspend fun completePasswordRecovery(email: String, code: String, newPassword: String) {
        passwordProblem(newPassword)?.let { throw UserFacingException(it) }
        client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email.trim(), token = code.trim())
        try {
            client.auth.updateUser { password = newPassword }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "No se pudo guardar la contraseña nueva", error)
            throw UserFacingException(
                "El código era correcto, pero no se pudo guardar la contraseña nueva. Cámbiala en Más › Cambiar mi contraseña.",
            )
        }
    }

    /** Cambia la contraseña propia; el trigger de staging limpia `debe_cambiar_contrasena`. */
    suspend fun changeOwnPassword(password: String) {
        passwordProblem(password)?.let { throw UserFacingException(it) }
        client.auth.updateUser { this.password = password }
    }

    /**
     * Ajusta las membresías a las deseadas (ministryId -> es líder). `set_usuario_ministerios`
     * agrega y quita membresías conservando `es_lider` de las existentes; el liderazgo se ajusta aparte.
     */
    suspend fun syncMemberships(target: Member, ministries: List<Ministry>, desired: Map<String, Boolean>) {
        val currentLeader = ministries.filter { target.id in it.leaderIds }.mapTo(mutableSetOf(), Ministry::id)
        client.postgrest.rpc(
            function = "set_usuario_ministerios",
            parameters = buildJsonObject {
                put("p_usuario_id", target.id)
                putJsonArray("p_ministerio_ids") { desired.keys.forEach { add(it) } }
            },
        )
        desired.forEach { (ministryId, isLeader) ->
            if ((ministryId in currentLeader) != isLeader) setMembershipLeader(ministryId, target.id, isLeader)
        }
    }

    /** Nunca envía `correo`: un trigger de staging lo protege. */
    suspend fun updateProfileAsPastor(
        target: Member,
        fullName: String,
        phone: String,
        role: String,
        active: Boolean,
    ) {
        require(role in Roles.all) { "El rol seleccionado no es válido." }
        val (nombres, apellidos) = StagingMappings.splitFullName(fullName)
        client.from("usuario").update(
            UsuarioAdminUpdate(
                nombres = nombres,
                apellidos = apellidos,
                telefono = phone.trim().ifBlank { null },
                rol = StagingMappings.roleToStaging(role),
                estado = StagingMappings.userStatusToStaging(active),
            ),
        ) {
            filter { eq("id", target.id) }
        }
    }
}

/** Estados válidos de proyectos, actividades y tareas según los requerimientos. */
val WORK_STATUSES = listOf("pending", "in_progress", "paused", "completed")

private val MINISTRY_COLORS = listOf(
    0xFF2563EB,
    0xFF0F766E,
    0xFF7C3AED,
    0xFFB45309,
    0xFFBE185D,
)

private fun parseTimestamp(value: String): OffsetDateTime? =
    runCatching { OffsetDateTime.parse(value) }.getOrNull()

private fun String.initials(): String = split(Regex("\\s+"))
    .take(2)
    .mapNotNull(String::firstOrNull)
    .joinToString("")
    .uppercase()

private fun String.ministryIcon(): String = when {
    contains("alabanza", ignoreCase = true) -> "♫"
    contains("juventud", ignoreCase = true) -> "⚡"
    contains("niñ", ignoreCase = true) -> "☺"
    contains("mujer", ignoreCase = true) || contains("damas", ignoreCase = true) -> "❀"
    else -> "✝"
}

/** La base guarda "scheduled" como estado inicial de actividades y reuniones. */
private fun String.isPublicClientKey(): Boolean {
    if (startsWith("sb_publishable_")) return true
    if (isBlank() || startsWith("sb_secret_")) return false

    val payload = split(".").getOrNull(1) ?: return false
    return try {
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        JSONObject(String(decoded, StandardCharsets.UTF_8)).optString("role") == "anon"
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: org.json.JSONException) {
        false
    }
}

@Serializable
private data class ActividadRow(
    val id: String,
    val titulo: String,
    val tipo: String? = null,
    val clasificacion: String? = null,
    @SerialName("proyecto_id") val proyectoId: String? = null,
    @SerialName("ministerio_id") val ministerioId: String? = null,
    val descripcion: String? = null,
    val inicio: String,
    val fin: String? = null,
    val lugar: String? = null,
    val estado: String? = null,
)

@Serializable
private data class ActividadResponsableRow(
    @SerialName("actividad_id") val actividadId: String,
    @SerialName("usuario_id") val usuarioId: String,
)

@Serializable
private data class DetalleCultoRow(
    @SerialName("actividad_id") val actividadId: String,
    @SerialName("predicador_id") val predicadorId: String? = null,
    @SerialName("director_alabanza_id") val directorAlabanzaId: String? = null,
)

@Serializable
private data class ConvocatoriaRow(
    @SerialName("actividad_id") val actividadId: String,
    val alcance: String,
    @SerialName("ministerio_id") val ministerioId: String? = null,
    @SerialName("usuario_id") val usuarioId: String? = null,
)

@Serializable
private data class ResponsableNombreRow(
    @SerialName("usuario_id") val usuarioId: String,
    val nombres: String? = null,
    val apellidos: String? = null,
)

@Serializable
private data class SupabaseNotification(
    val id: String,
    val message: String,
    val type: String,
    @SerialName("created_at") val createdAt: String,
    val read: Boolean,
)

@Serializable
private data class ProyectoRow(
    val id: String,
    val nombre: String,
    val objetivo: String? = null,
    val descripcion: String? = null,
    @SerialName("responsable_id") val responsableId: String? = null,
    @SerialName("fecha_inicio") val fechaInicio: String? = null,
    @SerialName("fecha_cierre_estimada") val fechaCierreEstimada: String? = null,
    @SerialName("presupuesto_estimado") val presupuestoEstimado: Double? = null,
    @SerialName("presupuesto_ejecutado") val presupuestoEjecutado: Double? = null,
    val estado: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class ProyectoInsert(
    val nombre: String,
    val objetivo: String,
    val descripcion: String?,
    @SerialName("responsable_id") val responsableId: String,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_cierre_estimada") val fechaCierreEstimada: String,
    @SerialName("presupuesto_estimado") val presupuestoEstimado: Long?,
    @SerialName("presupuesto_ejecutado") val presupuestoEjecutado: Long?,
    val estado: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class ProyectoUpdate(
    val nombre: String,
    val objetivo: String,
    val descripcion: String?,
    @SerialName("responsable_id") val responsableId: String,
    @SerialName("fecha_inicio") val fechaInicio: String,
    @SerialName("fecha_cierre_estimada") val fechaCierreEstimada: String,
    @SerialName("presupuesto_estimado") val presupuestoEstimado: Long?,
    @SerialName("presupuesto_ejecutado") val presupuestoEjecutado: Long?,
    val estado: String,
)

@Serializable
private data class ActividadEstadoUpdate(val estado: String)

@Serializable
private data class ActividadFinUpdate(val fin: String)

@Serializable
private data class ActividadEstadoRow(val id: String)

@Serializable
private data class ActividadResponsableInsert(
    @SerialName("actividad_id") val actividadId: String,
    @SerialName("usuario_id") val usuarioId: String,
)

@Serializable
private data class DetalleCultoInsert(
    @SerialName("actividad_id") val actividadId: String,
    @SerialName("predicador_id") val predicadorId: String?,
    @SerialName("director_alabanza_id") val directorAlabanzaId: String?,
)

@Serializable
private data class ConvocatoriaInsert(
    @SerialName("actividad_id") val actividadId: String,
    val alcance: String,
    @SerialName("ministerio_id") val ministerioId: String?,
    @SerialName("usuario_id") val usuarioId: String?,
)

@Serializable
private data class IdRow(val id: String)

@Serializable
private data class TareaEstadoUpdate(val estado: String)

@Serializable
private data class TareaResponsableUpdate(@SerialName("responsable_id") val responsableId: String)

@Serializable
private data class TareaInsert(
    @SerialName("actividad_id") val actividadId: String,
    val titulo: String,
    val instrucciones: String?,
    @SerialName("responsable_id") val responsableId: String,
    @SerialName("fecha_limite") val fechaLimite: String?,
    val prioridad: String,
    val estado: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class ComentarioTareaInsert(
    @SerialName("tarea_id") val tareaId: String,
    @SerialName("autor_id") val autorId: String,
    val texto: String,
)

@Serializable
private data class TareaRow(
    val id: String,
    @SerialName("actividad_id") val actividadId: String,
    val titulo: String,
    val instrucciones: String? = null,
    @SerialName("responsable_id") val responsableId: String? = null,
    @SerialName("fecha_limite") val fechaLimite: String? = null,
    val prioridad: String? = null,
    val estado: String? = null,
)

@Serializable
private data class ComentarioTareaRow(
    val id: String,
    @SerialName("tarea_id") val tareaId: String,
    @SerialName("autor_id") val autorId: String? = null,
    val texto: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class NotificationReadUpdate(val read: Boolean)

@Serializable
private data class ArchivoRow(
    val id: String,
    val nombre: String,
    val ruta: String,
    val tipo: String,
    @SerialName("tamano_bytes") val tamanoBytes: Int,
    @SerialName("cancion_id") val cancionId: String? = null,
    @SerialName("reunion_id") val reunionId: String? = null,
)

@Serializable
private data class ArchivoInsert(
    val nombre: String,
    val ruta: String,
    val tipo: String,
    @SerialName("tamano_bytes") val tamanoBytes: Int,
    @SerialName("subido_por") val subidoPor: String,
    @SerialName("cancion_id") val cancionId: String? = null,
    @SerialName("reunion_id") val reunionId: String? = null,
    @SerialName("actividad_id") val actividadId: String? = null,
    @SerialName("ministerio_id") val ministerioId: String? = null,
)

@Serializable
private data class ReunionRow(
    val id: String,
    @SerialName("actividad_id") val actividadId: String? = null,
    val nombre: String,
    val fecha: String,
    val resumen: String? = null,
)

@Serializable
private data class ReunionInsert(
    val id: String,
    @SerialName("actividad_id") val actividadId: String?,
    val nombre: String,
    val fecha: String,
    val resumen: String?,
    @SerialName("registrado_por") val registradoPor: String,
)

@Serializable
private data class VisibilidadReunionRow(
    @SerialName("reunion_id") val reunionId: String,
    val alcance: String,
    @SerialName("ministerio_id") val ministerioId: String? = null,
    @SerialName("usuario_id") val usuarioId: String? = null,
)

@Serializable
private data class VisibilidadReunionInsert(
    @SerialName("reunion_id") val reunionId: String,
    val alcance: String,
    @SerialName("ministerio_id") val ministerioId: String?,
    @SerialName("usuario_id") val usuarioId: String?,
)

@Serializable
private data class CancionRow(
    val id: String,
    val titulo: String,
    val artista: String? = null,
    val tonalidad: String? = null,
    @SerialName("enlace_version") val enlaceVersion: String? = null,
)

@Serializable
private data class CancionInsert(
    val id: String,
    val titulo: String,
    val artista: String?,
    val tonalidad: String?,
    @SerialName("enlace_version") val enlaceVersion: String?,
    @SerialName("registrado_por") val registradoPor: String,
)

@Serializable
private data class CancionUpdate(
    val titulo: String,
    val artista: String?,
    val tonalidad: String?,
    @SerialName("enlace_version") val enlaceVersion: String?,
)

@Serializable
private data class RepertorioRow(
    val id: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class RepertorioInsert(
    val id: String,
    @SerialName("modificado_por") val modificadoPor: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class RepertorioUpdate(
    @SerialName("modificado_por") val modificadoPor: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class RepertorioCancionRow(
    @SerialName("repertorio_id") val repertorioId: String,
    @SerialName("cancion_id") val cancionId: String,
    val orden: Int,
    val tonalidad: String? = null,
    @SerialName("enlace_version") val enlaceVersion: String? = null,
    @SerialName("director_id") val directorId: String? = null,
)

@Serializable
private data class RepertorioCancionInsert(
    @SerialName("repertorio_id") val repertorioId: String,
    @SerialName("cancion_id") val cancionId: String,
    val orden: Int,
    val tonalidad: String?,
    @SerialName("enlace_version") val enlaceVersion: String?,
    @SerialName("director_id") val directorId: String?,
) {
    constructor(row: StagingWorship.RepertoireRow) :
        this(row.repertoireId, row.songId, row.orden, row.key, row.versionUrl, row.leadId)
}

@Serializable
private data class UsuarioRow(
    val id: String,
    val nombres: String = "",
    val apellidos: String = "",
    val correo: String? = null,
    val telefono: String? = null,
    val rol: String? = null,
    val estado: String? = null,
    @SerialName("debe_cambiar_contrasena") val debeCambiarContrasena: Boolean = false,
)

@Serializable
private data class MiembroMinisterioRow(
    @SerialName("ministerio_id") val ministerioId: String,
    @SerialName("usuario_id") val usuarioId: String,
    @SerialName("es_lider") val esLider: Boolean = false,
)

@Serializable
private data class MinisterioRow(
    val id: String,
    val nombre: String,
    val descripcion: String? = null,
    val estado: String? = null,
    @SerialName("es_alabanza") val esAlabanza: Boolean = false,
)

@Serializable
private data class MinisterioInsert(val nombre: String, val descripcion: String)

@Serializable
private data class MinisterioUpdate(val nombre: String, val descripcion: String)

@Serializable
private data class MinisterioEstadoUpdate(val estado: String)

@Serializable
private data class MiembroLiderUpdate(@SerialName("es_lider") val esLider: Boolean)

@Serializable
private data class UsuarioAdminUpdate(
    val nombres: String,
    val apellidos: String,
    val telefono: String?,
    val rol: String,
    val estado: String,
)

@Serializable
private data class UsuarioDetalleUpdate(val rol: String, val telefono: String?)

/** Resultado de `create-user`; la contraseña temporal solo existe en esta respuesta. */
class CreatedAccount(val id: String, val email: String, val password: String)

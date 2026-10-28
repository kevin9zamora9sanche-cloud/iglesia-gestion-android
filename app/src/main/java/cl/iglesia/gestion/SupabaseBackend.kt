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
    private const val RECORDS_BUCKET = "meeting-records"
    private const val SONGS_BUCKET = "worship-songs"

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

        val progressTasks = client.from("tarea")
            .select()
            .decodeList<ProgressTareaRow>()
            .groupBy(ProgressTareaRow::actividadId)
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

        val worshipMinistryId = ministries.firstOrNull(Ministry::isWorship)?.id
        // Archivos de canciones: requieren la migración 20261023130000; sin ella el
        // catálogo funciona igual, solo sin archivos.
        val songFiles = try {
            client.from("worship_song_files")
                .select { order("created_at", Order.ASCENDING) }
                .decodeList<SupabaseSongFile>()
                .groupBy(SupabaseSongFile::songId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "Archivos de canciones no disponibles", error)
            emptyMap()
        }
        val songs = worshipMinistryId?.let { ministryId ->
            client.from("worship_songs")
                .select {
                    filter {
                        eq("ministry_id", ministryId)
                    }
                    order("title", Order.ASCENDING)
                }
                .decodeList<SupabaseSong>()
                .map {
                    WorshipSong(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        defaultKey = it.defaultKey,
                        referenceUrl = it.referenceUrl,
                        files = songFiles[it.id].orEmpty().map { file ->
                            RecordFile(file.id, file.fileName, file.contentType, file.sizeBytes, file.objectPath, SONGS_BUCKET)
                        },
                    )
                }
        }.orEmpty()
        val setSongsBySet = client.from("worship_set_songs")
            .select()
            .decodeList<SupabaseWorshipSetSong>()
            .groupBy(SupabaseWorshipSetSong::setId)
        val worshipSets = client.from("worship_sets")
            .select()
            .decodeList<SupabaseWorshipSet>()
            .map { set ->
                WorshipSet(
                    id = set.id,
                    activityId = set.activityId,
                    directorId = set.directorId,
                    updatedAt = set.updatedAt?.let(::parseTimestamp),
                    songs = setSongsBySet[set.id].orEmpty()
                        .sortedBy(SupabaseWorshipSetSong::position)
                        .map {
                            WorshipSetSong(
                                songId = it.songId,
                                position = it.position,
                                key = it.songKey,
                                leadId = it.leadId,
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

        // Los registros dependen de la migración 20261022110000; si aún no está
        // aplicada, el resto de módulos sigue funcionando y la pantalla lo indica.
        val meetingRecords = try {
            loadMeetingRecords()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("SupabaseBackend", "Registros de reuniones no disponibles", error)
            null
        }

        return ChurchSnapshot(
            members = members + hiddenMembers,
            ministries = ministries,
            inactiveMinistries = inactiveMinistries,
            activities = churchActivities.sortedWith(
                compareBy(ChurchActivity::date, ChurchActivity::time),
            ),
            projects = projects,
            songs = songs,
            worshipSets = worshipSets,
            notifications = notifications,
            meetingRecords = meetingRecords.orEmpty(),
            recordsAvailable = meetingRecords != null,
        )
    }

    private suspend fun loadMeetingRecords(): List<MeetingRecord> {
        val viewersByRecord = client.from("meeting_record_viewers")
            .select()
            .decodeList<SupabaseRecordViewer>()
            .groupBy(SupabaseRecordViewer::recordId)
        val filesByRecord = client.from("meeting_record_files")
            .select { order("created_at", Order.ASCENDING) }
            .decodeList<SupabaseRecordFile>()
            .groupBy(SupabaseRecordFile::recordId)
        return client.from("meeting_records")
            .select { order("record_date", Order.DESCENDING) }
            .decodeList<SupabaseMeetingRecord>()
            .map { record ->
                MeetingRecord(
                    id = record.id,
                    meetingId = record.meetingId,
                    title = record.title,
                    date = LocalDate.parse(record.recordDate),
                    summary = record.summary,
                    visibility = RecordVisibility.entries.firstOrNull { it.databaseValue == record.visibility }
                        ?: RecordVisibility.Selected,
                    ministryId = record.ministryId,
                    viewerIds = viewersByRecord[record.id].orEmpty().map(SupabaseRecordViewer::userId),
                    files = filesByRecord[record.id].orEmpty().map {
                        RecordFile(it.id, it.fileName, it.contentType, it.sizeBytes, it.objectPath)
                    },
                )
            }
    }

    /**
     * Crea el registro y sube sus archivos. Si un archivo falla, el registro queda
     * guardado y se informa exactamente qué archivos no se adjuntaron.
     */
    suspend fun createMeetingRecord(currentMember: Member, draft: MeetingRecordDraft, files: List<PickedFile>) {
        require(currentMember.isPastor) { "Solo el Pastor/Administrador registra reuniones." }
        val recordId = UUID.randomUUID().toString()
        client.from("meeting_records").insert(
            MeetingRecordInsert(
                id = recordId,
                meetingId = draft.meetingId,
                title = draft.title.trim(),
                recordDate = draft.date.toString(),
                summary = draft.summary.trim(),
                visibility = draft.visibility.databaseValue,
                ministryId = draft.ministryId.takeIf { draft.visibility == RecordVisibility.Ministry },
                createdBy = currentMember.id,
            ),
        )
        if (draft.visibility == RecordVisibility.Selected && draft.viewerIds.isNotEmpty()) {
            client.from("meeting_record_viewers").insert(
                draft.viewerIds.distinct().map { SupabaseRecordViewer(recordId, it) },
            )
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

    suspend fun uploadRecordFile(currentMember: Member, recordId: String, file: PickedFile) {
        val extension = ALLOWED_FILE_TYPES[file.contentType]
            ?: throw UserFacingException("Solo se permiten archivos PDF, JPG o PNG.")
        if (file.bytes.isEmpty() || file.bytes.size > MAX_FILE_BYTES) {
            throw UserFacingException("Cada archivo debe pesar como máximo 5 MB.")
        }
        val fileId = UUID.randomUUID().toString()
        val path = "$recordId/$fileId.$extension"
        val bucket = client.storage.from(RECORDS_BUCKET)
        bucket.upload(path, file.bytes, upsert = false)
        try {
            client.from("meeting_record_files").insert(
                RecordFileInsert(
                    id = fileId,
                    recordId = recordId,
                    fileName = file.name,
                    contentType = file.contentType,
                    sizeBytes = file.bytes.size.toLong(),
                    objectPath = path,
                    uploadedBy = currentMember.id,
                ),
            )
        } catch (error: Exception) {
            // Sin fila asociada el objeto quedaría huérfano: se elimina.
            runCatching { bucket.delete(path) }
            throw error
        }
    }

    suspend fun deleteRecordFile(file: RecordFile) {
        client.from("meeting_record_files").delete {
            filter { eq("id", file.id) }
        }
        client.storage.from(RECORDS_BUCKET).delete(file.objectPath)
    }

    suspend fun deleteMeetingRecord(record: MeetingRecord) {
        if (record.files.isNotEmpty()) {
            client.storage.from(RECORDS_BUCKET).delete(record.files.map(RecordFile::objectPath))
        }
        client.from("meeting_records").delete {
            filter { eq("id", record.id) }
        }
    }

    /** Enlace temporal de descarga; Storage valida los permisos del registro o la canción. */
    suspend fun signedFileUrl(file: RecordFile): String {
        val url = client.storage.from(file.bucket).createSignedUrl(file.objectPath, 5.minutes)
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
        client.from("tasks").insert(
            TaskInsert(
                id = UUID.randomUUID().toString(),
                activityId = draft.activityId,
                title = draft.title.trim(),
                instructions = draft.instructions.trim(),
                assigneeId = draft.assigneeId,
                dueAt = draft.dueAt?.toString(),
                priority = draft.priority,
                status = "pending",
                comments = draft.comments.trim(),
                assignedBy = currentMember.id,
            ),
        )
    }

    suspend fun updateTaskStatus(taskId: String, status: String) {
        require(status in WORK_STATUSES) { "El estado seleccionado no es válido." }
        client.from("tasks").update(TaskStatusUpdate(status)) {
            filter {
                eq("id", taskId)
            }
        }
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
        ministryId: String,
        draft: WorshipSongDraft,
        files: List<PickedFile> = emptyList(),
    ) {
        val songId = UUID.randomUUID().toString()
        client.from("worship_songs").insert(
            WorshipSongInsert(
                id = songId,
                ministryId = ministryId,
                title = draft.title.trim(),
                artist = draft.artist.trim(),
                defaultKey = draft.defaultKey.trim(),
                referenceUrl = draft.referenceUrl.trim(),
                createdBy = currentMember.id,
            ),
        )
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
        client.from("worship_songs").update(
            WorshipSongUpdate(
                title = draft.title.trim(),
                artist = draft.artist.trim(),
                defaultKey = draft.defaultKey.trim(),
                referenceUrl = draft.referenceUrl.trim(),
            ),
        ) {
            filter { eq("id", songId) }
        }
    }

    /**
     * Elimina una canción y sus archivos. Una canción usada en algún repertorio no se
     * puede eliminar (la base lo impide con una clave foránea).
     */
    suspend fun deleteWorshipSong(song: WorshipSong, worshipSets: List<WorshipSet>) {
        if (worshipSets.any { set -> set.songs.any { it.songId == song.id } }) {
            throw UserFacingException(
                "\"${song.title}\" está en el repertorio, por lo que no se puede eliminar. " +
                    "Quítala del repertorio primero o edita sus datos.",
            )
        }
        // Los objetos de Storage se borran antes: su permiso depende de que la canción exista.
        if (song.files.isNotEmpty()) {
            client.storage.from(SONGS_BUCKET).delete(song.files.map(RecordFile::objectPath))
        }
        client.from("worship_songs").delete {
            filter { eq("id", song.id) }
        }
    }

    suspend fun uploadSongFile(currentMember: Member, songId: String, file: PickedFile) {
        val extension = ALLOWED_FILE_TYPES[file.contentType]
            ?: throw UserFacingException("Solo se permiten archivos PDF, JPG o PNG.")
        if (file.bytes.isEmpty() || file.bytes.size > MAX_FILE_BYTES) {
            throw UserFacingException("Cada archivo debe pesar como máximo 5 MB.")
        }
        val fileId = UUID.randomUUID().toString()
        val path = "$songId/$fileId.$extension"
        val bucket = client.storage.from(SONGS_BUCKET)
        bucket.upload(path, file.bytes, upsert = false)
        try {
            client.from("worship_song_files").insert(
                SongFileInsert(
                    id = fileId,
                    songId = songId,
                    fileName = file.name,
                    contentType = file.contentType,
                    sizeBytes = file.bytes.size.toLong(),
                    objectPath = path,
                    uploadedBy = currentMember.id,
                ),
            )
        } catch (error: Exception) {
            // Sin fila asociada el objeto quedaría huérfano: se elimina.
            runCatching { bucket.delete(path) }
            throw error
        }
    }

    suspend fun deleteSongFile(file: RecordFile) {
        client.from("worship_song_files").delete {
            filter { eq("id", file.id) }
        }
        client.storage.from(SONGS_BUCKET).delete(file.objectPath)
    }

    /**
     * Reemplaza el repertorio único del ministerio en una sola transacción; la base
     * envía un solo aviso. Requiere la migración 20261023140000.
     */
    suspend fun saveMinistryRepertoire(ministryId: String, songs: List<WorshipSetSong>) {
        client.postgrest.rpc(
            function = "save_ministry_repertoire",
            parameters = buildJsonObject {
                put("target_ministry_id", ministryId)
                putJsonArray("target_songs") {
                    songs.forEach { song ->
                        addJsonObject {
                            put("song_id", song.songId)
                            put("song_key", song.key)
                            put("lead_id", song.leadId.orEmpty())
                        }
                    }
                }
            },
        )
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
        client.from("tasks").update(TaskAssigneeUpdate(assigneeId)) {
            filter { eq("id", taskId) }
        }
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
private data class TaskAssigneeUpdate(
    @SerialName("assignee_id") val assigneeId: String,
)

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
private data class SupabaseSong(
    val id: String,
    val title: String,
    val artist: String,
    @SerialName("default_key") val defaultKey: String,
    @SerialName("reference_url") val referenceUrl: String = "",
)

@Serializable
private data class SupabaseWorshipSet(
    val id: String,
    @SerialName("activity_id") val activityId: String? = null,
    @SerialName("director_id") val directorId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class SupabaseWorshipSetSong(
    @SerialName("set_id") val setId: String,
    @SerialName("song_id") val songId: String,
    val position: Int,
    @SerialName("song_key") val songKey: String,
    @SerialName("lead_id") val leadId: String? = null,
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
private data class ProgressTareaRow(
    @SerialName("actividad_id") val actividadId: String,
    val estado: String? = null,
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
private data class TaskStatusUpdate(
    val status: String,
)

@Serializable
private data class TaskInsert(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    val title: String,
    val instructions: String,
    @SerialName("assignee_id") val assigneeId: String,
    @SerialName("due_at") val dueAt: String?,
    val priority: String,
    val status: String,
    val comments: String,
    @SerialName("assigned_by") val assignedBy: String,
)

@Serializable
private data class NotificationReadUpdate(val read: Boolean)

@Serializable
private data class WorshipSongInsert(
    val id: String,
    @SerialName("ministry_id") val ministryId: String,
    val title: String,
    val artist: String,
    @SerialName("default_key") val defaultKey: String,
    @SerialName("reference_url") val referenceUrl: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class SupabaseMeetingRecord(
    val id: String,
    @SerialName("meeting_id") val meetingId: String? = null,
    val title: String,
    @SerialName("record_date") val recordDate: String,
    val summary: String = "",
    val visibility: String,
    @SerialName("ministry_id") val ministryId: String? = null,
)

@Serializable
private data class MeetingRecordInsert(
    val id: String,
    @SerialName("meeting_id") val meetingId: String?,
    val title: String,
    @SerialName("record_date") val recordDate: String,
    val summary: String,
    val visibility: String,
    @SerialName("ministry_id") val ministryId: String?,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class SupabaseRecordViewer(
    @SerialName("record_id") val recordId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class SupabaseRecordFile(
    val id: String,
    @SerialName("record_id") val recordId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("object_path") val objectPath: String,
)

@Serializable
private data class WorshipSongUpdate(
    val title: String,
    val artist: String,
    @SerialName("default_key") val defaultKey: String,
    @SerialName("reference_url") val referenceUrl: String,
)

@Serializable
private data class SupabaseSongFile(
    val id: String,
    @SerialName("song_id") val songId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("object_path") val objectPath: String,
)

@Serializable
private data class SongFileInsert(
    val id: String,
    @SerialName("song_id") val songId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("object_path") val objectPath: String,
    @SerialName("uploaded_by") val uploadedBy: String,
)

@Serializable
private data class RecordFileInsert(
    val id: String,
    @SerialName("record_id") val recordId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("object_path") val objectPath: String,
    @SerialName("uploaded_by") val uploadedBy: String,
)

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

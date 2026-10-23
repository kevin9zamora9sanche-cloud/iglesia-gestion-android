package cl.iglesia.gestion

import android.util.Base64
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
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

    private const val RECORDS_BUCKET = "meeting-records"

    suspend fun loadCurrentMember(): Member {
        val user = client.auth.retrieveUserForCurrentSession()
        val profile = client.from("profiles")
            .select {
                filter {
                    eq("id", user.id)
                }
            }
            .decodeSingle<SupabaseProfile>()

        check(profile.isActive) {
            "Esta cuenta está inactiva. Contacta al Pastor/Administrador."
        }

        val memberships = client.from("ministry_memberships")
            .select {
                filter {
                    eq("user_id", user.id)
                }
            }
            .decodeList<SupabaseMembership>()
        val role = profile.role.toRoleLabel()
        val displayName = profile.fullName.ifBlank {
            user.email?.substringBefore("@")?.takeIf(String::isNotBlank) ?: "Usuario"
        }
        return Member(
            id = profile.id,
            name = displayName,
            role = role,
            initials = displayName.initials(),
            email = user.email.orEmpty(),
            active = profile.isActive,
            phone = profile.phone.orEmpty(),
            ministryIds = memberships.map(SupabaseMembership::ministryId),
            ledMinistryIds = memberships
                .filter { it.isLeader && role == Roles.MINISTRY_LEADER }
                .map(SupabaseMembership::ministryId),
        )
    }

    suspend fun loadChurchSnapshot(currentMember: Member): ChurchSnapshot {
        val ministryRows = client.from("ministries")
            .select { order("name", Order.ASCENDING) }
            .decodeList<SupabaseMinistry>()
        val membershipRows = client.from("ministry_memberships")
            .select()
            .decodeList<SupabaseMembership>()
        val memberRows = client.from("profiles")
            .select { order("full_name", Order.ASCENDING) }
            .decodeList<SupabaseProfile>()
        val membershipsByUser = membershipRows.groupBy(SupabaseMembership::userId)
        // Correo y último acceso solo para el Pastor; requiere la migración 20261023100000.
        val accounts = if (currentMember.isPastor) {
            try {
                client.postgrest.rpc("admin_list_users").decodeList<SupabaseAccount>().associateBy(SupabaseAccount::id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("SupabaseBackend", "Directorio de cuentas no disponible", error)
                emptyMap()
            }
        } else {
            emptyMap()
        }
        val members = memberRows.map { profile ->
            val name = profile.fullName.ifBlank {
                if (profile.id == currentMember.id) currentMember.name else "Usuario"
            }
            val role = profile.role.toRoleLabel()
            val memberships = membershipsByUser[profile.id].orEmpty()
            Member(
                id = profile.id,
                name = name,
                role = role,
                initials = name.initials(),
                email = if (profile.id == currentMember.id) currentMember.email else accounts[profile.id]?.email.orEmpty(),
                active = profile.isActive,
                phone = profile.phone.orEmpty(),
                ministryIds = memberships.map(SupabaseMembership::ministryId),
                ledMinistryIds = memberships
                    .filter { it.isLeader && role == Roles.MINISTRY_LEADER }
                    .map(SupabaseMembership::ministryId),
                lastSignIn = accounts[profile.id]?.lastSignInAt?.let(::parseTimestamp),
            )
        }
        val allMinistries = ministryRows.mapIndexed { index, ministry ->
            Ministry(
                id = ministry.id,
                name = ministry.name,
                icon = ministry.name.ministryIcon(),
                color = MINISTRY_COLORS[index % MINISTRY_COLORS.size],
                leaderIds = membershipRows
                    .filter { it.ministryId == ministry.id && it.isLeader }
                    .map(SupabaseMembership::userId),
                description = ministry.description,
                active = ministry.isActive,
            )
        }
        val (ministries, inactiveMinistries) = allMinistries.partition(Ministry::active)

        val activities = client.from("activities")
            .select()
            .decodeList<SupabaseActivity>()
        val tasks = client.from("tasks")
            .select()
            .decodeList<SupabaseTask>()
        // RLS devuelve las asignaciones propias y las de actividades que el usuario administra.
        val assignmentsByActivity = client.from("activity_assignments")
            .select()
            .decodeList<SupabaseActivityAssignment>()
            .groupBy(SupabaseActivityAssignment::activityId)
            .mapValues { (_, rows) -> rows.map(SupabaseActivityAssignment::userId) }

        val tasksByActivity = tasks.groupBy(SupabaseTask::activityId)
        val activitiesByProject = activities
            .filter { it.projectId != null && it.kind == "activity" }
            .groupBy { it.projectId }
        val projects = client.from("projects")
            .select { order("due_date", Order.ASCENDING) }
            .decodeList<SupabaseProject>()
            .map { project ->
                ChurchProject(
                    id = project.id,
                    name = project.name,
                    objective = project.objective,
                    description = project.description,
                    leaderId = project.leaderId,
                    startDate = LocalDate.parse(project.startDate),
                    dueDate = LocalDate.parse(project.dueDate),
                    // El Pastor ve todas las actividades y tareas, así que el cálculo
                    // local es exacto e inmediato. Los demás ven solo una parte por RLS:
                    // para ellos se usa el avance que la base recalcula con todo.
                    progress = if (currentMember.isPastor) {
                        calculateProjectProgress(activitiesByProject[project.id].orEmpty(), tasksByActivity)
                    } else {
                        project.progress.coerceIn(0, 100)
                    },
                    status = project.status,
                    budgetEstimated = project.budget?.toLong(),
                    budgetExecuted = project.budgetExecuted?.toLong(),
                )
            }

        val churchActivities = activities.map { activity ->
            val assigneeIds = assignmentsByActivity[activity.id].orEmpty()
            activity.toChurchActivity(
                assigneeIds = assigneeIds,
                isAssignedToCurrentUser = currentMember.id in assigneeIds,
            )
        }
        val activityById = activities.associateBy(SupabaseActivity::id)
        // Una tarea cuyo padre no es visible por RLS se omite en vez de invalidar toda la carga.
        val churchTasks = tasks.mapNotNull { task ->
            val parentActivity = activityById[task.activityId] ?: return@mapNotNull null
            val dueAt = task.dueAt?.let(::parseTimestamp)
            val localDue = dueAt?.atZoneSameInstant(ZoneId.systemDefault())
            ChurchActivity(
                id = task.id,
                title = task.title,
                description = task.instructions,
                date = localDue?.toLocalDate() ?: LocalDate.parse(parentActivity.activityDate),
                time = localDue?.toLocalTime()?.toString()?.take(5) ?: parentActivity.startTime.take(5),
                endTime = localDue?.toLocalTime()?.toString()?.take(5) ?: parentActivity.endTime.take(5),
                ministryId = parentActivity.ministryId,
                location = parentActivity.location,
                status = task.status,
                kind = ItemKind.Task,
                classification = parentActivity.classification(),
                projectId = parentActivity.projectId,
                assigneeIds = listOf(task.assigneeId),
                isAssignedToCurrentUser = task.assigneeId == currentMember.id,
                parentActivityId = parentActivity.id,
                parentActivityTitle = parentActivity.title,
                priority = task.priority,
                comments = task.comments,
                dueAt = dueAt,
            )
        }

        val participantsByMeeting = client.from("meeting_participants")
            .select()
            .decodeList<SupabaseMeetingParticipant>()
            .groupBy(SupabaseMeetingParticipant::meetingId)
            .mapValues { (_, rows) -> rows.map(SupabaseMeetingParticipant::userId) }
        val meetings = client.from("meetings")
            .select()
            .decodeList<SupabaseMeeting>()
            .map { meeting ->
                val participants = participantsByMeeting[meeting.id].orEmpty()
                meeting.toChurchActivity(
                    participantIds = participants,
                    isAssignedToCurrentUser = currentMember.id in participants,
                )
            }

        val worshipMinistryId = ministries.firstOrNull(Ministry::isWorship)?.id
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
            members = members,
            ministries = ministries,
            inactiveMinistries = inactiveMinistries,
            activities = (churchActivities + churchTasks + meetings).sortedWith(
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

    /** Enlace temporal de descarga; Storage valida los permisos del registro. */
    suspend fun signedFileUrl(file: RecordFile): String {
        val url = client.storage.from(RECORDS_BUCKET).createSignedUrl(file.objectPath, 5.minutes)
        return if (url.startsWith("http")) url else BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/" + url.trimStart('/')
    }

    suspend fun createProject(currentMember: Member, draft: ProjectDraft) {
        require(currentMember.isPastor) {
            "Solo el Pastor/Administrador puede crear proyectos."
        }
        client.from("projects").insert(
            ProjectInsert(
                id = UUID.randomUUID().toString(),
                name = draft.name.trim(),
                objective = draft.objective.trim(),
                description = draft.description.trim(),
                leaderId = draft.leaderId,
                startDate = draft.startDate.toString(),
                dueDate = draft.dueDate.toString(),
                budget = draft.budgetEstimated,
                budgetExecuted = draft.budgetExecuted,
                status = draft.status,
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
        client.from("projects").update(
            ProjectUpdatePayload(
                name = draft.name.trim(),
                objective = draft.objective.trim(),
                description = draft.description.trim(),
                leaderId = draft.leaderId,
                startDate = draft.startDate.toString(),
                dueDate = draft.dueDate.toString(),
                budget = draft.budgetEstimated,
                budgetExecuted = draft.budgetExecuted,
                status = draft.status,
            ),
        ) {
            filter {
                eq("id", projectId)
            }
        }
    }

    suspend fun createActivity(currentMember: Member, draft: ActivityDraft) {
        require(draft.assigneeIds.isNotEmpty()) { "Selecciona al menos un responsable." }
        val ministryId = draft.ministryId.takeIf { draft.classification == Classification.Ministry }
        val projectId = draft.projectId.takeIf { draft.classification == Classification.Project }
        require(currentMember.isPastor || currentMember.canManageMinistry(ministryId)) {
            "Solo puedes crear actividades dentro de los ministerios que lideras."
        }
        val isCulto = draft.category == ActivityTypes.CULTO
        val activityId = UUID.randomUUID().toString()
        client.from("activities").insert(
            ActivityInsert(
                id = activityId,
                title = draft.title.trim(),
                description = draft.description.trim(),
                activityDate = draft.date.toString(),
                startTime = draft.startTime.toString(),
                endTime = draft.endTime.toString(),
                ministryId = ministryId,
                activityType = draft.classification.databaseValue,
                kind = "activity",
                location = draft.location.trim(),
                category = draft.category,
                projectId = projectId,
                worshipLeaderId = draft.worshipLeaderId.takeIf { isCulto },
                preacherId = draft.preacherId.takeIf { isCulto },
                createdBy = currentMember.id,
            ),
        )
        client.from("activity_assignments").insert(
            draft.assigneeIds.distinct().map { userId ->
                ActivityAssignmentInsert(
                    activityId = activityId,
                    userId = userId,
                    assignedBy = currentMember.id,
                )
            },
        )
    }

    suspend fun createMeeting(currentMember: Member, draft: MeetingDraft) {
        require(currentMember.isPastor || currentMember.canManageMinistry(draft.ministryId)) {
            "Solo puedes convocar reuniones de los ministerios que lideras."
        }
        val meetingId = UUID.randomUUID().toString()
        client.from("meetings").insert(
            MeetingInsert(
                id = meetingId,
                title = draft.title.trim(),
                purpose = draft.purpose.trim(),
                meetingDate = draft.date.toString(),
                startTime = draft.startTime.toString(),
                endTime = draft.endTime.toString(),
                location = draft.location.trim(),
                ministryId = draft.ministryId,
                createdBy = currentMember.id,
            ),
        )
        if (draft.participantIds.isNotEmpty()) {
            client.from("meeting_participants").insert(
                draft.participantIds.distinct().map { userId ->
                    MeetingParticipantInsert(meetingId, userId)
                },
            )
        }
    }

    suspend fun updateActivityStatus(activityId: String, status: String) {
        require(status in WORK_STATUSES) { "El estado seleccionado no es válido." }
        client.from("activities").update(ActivityStatusUpdate(status.toEventStatus())) {
            filter {
                eq("id", activityId)
                eq("kind", "activity")
            }
        }
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

    suspend fun updateLegacyTaskStatus(activityId: String, status: String) {
        require(status in WORK_STATUSES) { "El estado seleccionado no es válido." }
        client.from("activities").update(LegacyTaskStatusUpdate(status)) {
            filter {
                eq("id", activityId)
                eq("kind", "task")
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

    suspend fun createWorshipSong(currentMember: Member, ministryId: String, draft: WorshipSongDraft) {
        client.from("worship_songs").insert(
            WorshipSongInsert(
                id = UUID.randomUUID().toString(),
                ministryId = ministryId,
                title = draft.title.trim(),
                artist = draft.artist.trim(),
                defaultKey = draft.defaultKey.trim(),
                referenceUrl = draft.referenceUrl.trim(),
                createdBy = currentMember.id,
            ),
        )
    }

    /** Reemplaza el repertorio completo de un Culto en una sola transacción. */
    suspend fun saveWorshipSet(activityId: String, ministryId: String, songs: List<WorshipSetSong>) {
        client.postgrest.rpc(
            function = "save_worship_set",
            parameters = buildJsonObject {
                put("target_activity_id", activityId)
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
        client.from("ministries").insert(
            MinistryInsert(
                id = UUID.randomUUID().toString(),
                name = draft.name.trim(),
                description = draft.description.trim(),
                createdBy = currentMember.id,
            ),
        )
    }

    suspend fun updateMinistry(currentMember: Member, ministryId: String, draft: MinistryDraft) {
        require(currentMember.isPastor) { "Solo el Pastor/Administrador puede editar ministerios." }
        client.from("ministries").update(MinistryUpdate(draft.name.trim(), draft.description.trim())) {
            filter { eq("id", ministryId) }
        }
    }

    /** Desactiva o reactiva un ministerio; sus integrantes y registros se conservan. */
    suspend fun setMinistryActive(currentMember: Member, ministryId: String, active: Boolean) {
        require(currentMember.isPastor) { "Solo el Pastor/Administrador puede activar o desactivar ministerios." }
        client.from("ministries").update(MinistryActiveUpdate(active)) {
            filter { eq("id", ministryId) }
        }
    }

    suspend fun addMembership(ministryId: String, userId: String, isLeader: Boolean) {
        client.from("ministry_memberships").insert(
            SupabaseMembership(ministryId = ministryId, userId = userId, isLeader = isLeader),
        )
    }

    suspend fun setMembershipLeader(ministryId: String, userId: String, isLeader: Boolean) {
        client.from("ministry_memberships").update(MembershipLeaderUpdate(isLeader)) {
            filter {
                eq("ministry_id", ministryId)
                eq("user_id", userId)
            }
        }
    }

    suspend fun removeMembership(ministryId: String, userId: String) {
        client.from("ministry_memberships").delete {
            filter {
                eq("ministry_id", ministryId)
                eq("user_id", userId)
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
            client.from("activity_assignments").insert(
                toAdd.map { ActivityAssignmentInsert(activity.id, it, currentMember.id) },
            )
        }
        toRemove.forEach { userId ->
            client.from("activity_assignments").delete {
                filter {
                    eq("activity_id", activity.id)
                    eq("user_id", userId)
                }
            }
        }
    }

    suspend fun reassignTask(taskId: String, assigneeId: String) {
        client.from("tasks").update(TaskAssigneeUpdate(assigneeId)) {
            filter { eq("id", taskId) }
        }
    }

    suspend fun createUserAccount(draft: NewUserDraft): String {
        val response = invokeAdminUsers(
            buildJsonObject {
                put("action", "create")
                put("email", draft.email.trim())
                put("full_name", draft.fullName.trim())
                put("phone", draft.phone.trim())
                put("role", draft.role.toDatabaseRole())
                put("password", draft.password)
                putJsonArray("memberships") {
                    draft.memberships.forEach { (ministryId, isLeader) ->
                        addJsonObject {
                            put("ministry_id", ministryId)
                            put("is_leader", isLeader)
                        }
                    }
                }
            },
        )
        return response["id"]?.jsonPrimitive?.content.orEmpty()
    }

    suspend fun setTemporaryPassword(userId: String, password: String) {
        invokeAdminUsers(
            buildJsonObject {
                put("action", "set_password")
                put("user_id", userId)
                put("password", password)
            },
        )
    }

    suspend fun sendPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email.trim())
    }

    suspend fun changeOwnPassword(password: String) {
        passwordProblem(password)?.let { throw UserFacingException(it) }
        client.auth.updateUser { this.password = password }
    }

    /** Llama a la Edge Function admin-users y traduce su error a un mensaje legible. */
    private suspend fun invokeAdminUsers(body: JsonObject): JsonObject {
        val response = try {
            client.functions.invoke("admin-users", body)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val serverMessage = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(error.message.orEmpty())?.groupValues?.get(1)
            throw UserFacingException(
                serverMessage ?: "No se pudo contactar el servicio de cuentas. Verifica que la función admin-users esté desplegada.",
            )
        }
        val text = response.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        if (!response.status.isSuccess()) {
            throw UserFacingException(json["error"]?.jsonPrimitive?.content ?: "El servicio de cuentas rechazó la solicitud.")
        }
        return json
    }

    /** Ajusta las membresías del perfil a las deseadas (ministryId -> es líder). */
    suspend fun syncMemberships(target: Member, ministries: List<Ministry>, desired: Map<String, Boolean>) {
        val currentLeader = ministries.filter { target.id in it.leaderIds }.mapTo(mutableSetOf(), Ministry::id)
        val current = target.ministryIds.toSet()
        desired.forEach { (ministryId, isLeader) ->
            when {
                ministryId !in current -> addMembership(ministryId, target.id, isLeader)
                (ministryId in currentLeader) != isLeader -> setMembershipLeader(ministryId, target.id, isLeader)
            }
        }
        (current - desired.keys).forEach { removeMembership(it, target.id) }
    }

    suspend fun updateProfileAsPastor(
        target: Member,
        fullName: String,
        phone: String,
        role: String,
        active: Boolean,
    ) {
        val databaseRole = when (role) {
            Roles.PASTOR -> "pastor"
            Roles.MINISTRY_LEADER -> "ministry_leader"
            Roles.LEADER -> "leader"
            else -> error("El rol seleccionado no es válido.")
        }
        client.postgrest.rpc(
            function = "admin_update_profile",
            parameters = buildJsonObject {
                put("target_profile_id", target.id)
                put("target_full_name", fullName.trim())
                put("target_phone", phone.trim())
                put("target_role", databaseRole)
                put("target_is_active", active)
            },
        )
    }
}

/** Estados válidos de proyectos, actividades y tareas según los requerimientos. */
val WORK_STATUSES = listOf("pending", "in_progress", "paused", "completed")

/**
 * Avance propuesto: una actividad sin tareas vale 100 % solo si está completada;
 * con tareas, su fracción completada. El proyecto promedia sus actividades y
 * vale 0 cuando no tiene ninguna.
 */
private fun calculateProjectProgress(
    activities: List<SupabaseActivity>,
    tasksByActivity: Map<String, List<SupabaseTask>>,
): Int {
    if (activities.isEmpty()) return 0
    return activities.map { activity ->
        val activityTasks = tasksByActivity[activity.id].orEmpty()
        if (activityTasks.isEmpty()) {
            if (activity.status == "completed") 100.0 else 0.0
        } else {
            activityTasks.count { it.status == "completed" } * 100.0 / activityTasks.size
        }
    }.average().roundToInt().coerceIn(0, 100)
}

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
private fun String.toEventStatus(): String = if (this == "pending") "scheduled" else this

private fun String.fromEventStatus(): String = if (this == "scheduled") "pending" else this

private fun SupabaseActivity.classification(): Classification = when {
    activityType == "personal" -> Classification.Individual
    activityType == "ministry" -> Classification.Ministry
    projectId != null -> Classification.Project
    else -> Classification.General
}

private fun SupabaseActivity.toChurchActivity(
    assigneeIds: List<String>,
    isAssignedToCurrentUser: Boolean,
) = ChurchActivity(
    id = id,
    title = title,
    description = description,
    date = LocalDate.parse(activityDate),
    time = startTime.take(5),
    endTime = endTime.take(5),
    ministryId = ministryId,
    location = location,
    status = if (kind == "task") taskStatus ?: "pending" else status.fromEventStatus(),
    kind = if (kind == "task") ItemKind.Task else ItemKind.Activity,
    category = category,
    classification = classification(),
    projectId = projectId,
    worshipLeaderId = worshipLeaderId,
    preacherId = preacherId,
    assigneeIds = assigneeIds,
    isAssignedToCurrentUser = isAssignedToCurrentUser,
    priority = priority,
    isLegacyTask = kind == "task",
)

private fun SupabaseMeeting.toChurchActivity(
    participantIds: List<String>,
    isAssignedToCurrentUser: Boolean,
) = ChurchActivity(
    id = id,
    title = title,
    description = purpose,
    date = LocalDate.parse(meetingDate),
    time = startTime.take(5),
    endTime = endTime.take(5),
    ministryId = ministryId,
    location = location,
    status = status.fromEventStatus(),
    kind = ItemKind.Meeting,
    classification = if (ministryId != null) Classification.Ministry else Classification.General,
    assigneeIds = participantIds,
    isAssignedToCurrentUser = isAssignedToCurrentUser,
)

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

private fun String.toDatabaseRole(): String = when (this) {
    Roles.PASTOR -> "pastor"
    Roles.MINISTRY_LEADER -> "ministry_leader"
    else -> "leader"
}

private fun String.toRoleLabel(): String = when (this) {
    "pastor" -> Roles.PASTOR
    "ministry_leader" -> Roles.MINISTRY_LEADER
    "leader" -> Roles.LEADER
    else -> error("El rol de esta cuenta no está reconocido.")
}

@Serializable
private data class SupabaseProfile(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    val role: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
private data class SupabaseMembership(
    @SerialName("ministry_id") val ministryId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("is_leader") val isLeader: Boolean,
)

@Serializable
private data class SupabaseMinistry(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
private data class SupabaseProject(
    val id: String,
    val name: String,
    val objective: String = "",
    val description: String,
    @SerialName("leader_id") val leaderId: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("due_date") val dueDate: String,
    val budget: Double? = null,
    @SerialName("budget_executed") val budgetExecuted: Double? = null,
    val progress: Int = 0,
    val status: String,
)

@Serializable
private data class SupabaseAccount(
    val id: String,
    val email: String? = null,
    @SerialName("last_sign_in_at") val lastSignInAt: String? = null,
)

@Serializable
private data class TaskAssigneeUpdate(
    @SerialName("assignee_id") val assigneeId: String,
)

@Serializable
private data class SupabaseActivity(
    val id: String,
    val title: String,
    val description: String,
    @SerialName("activity_date") val activityDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("ministry_id") val ministryId: String? = null,
    @SerialName("activity_type") val activityType: String = "general",
    val location: String,
    val status: String,
    val kind: String,
    val category: String = "",
    val priority: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("worship_leader_id") val worshipLeaderId: String? = null,
    @SerialName("preacher_id") val preacherId: String? = null,
    @SerialName("task_status") val taskStatus: String? = null,
)

@Serializable
private data class SupabaseTask(
    val id: String,
    @SerialName("activity_id") val activityId: String,
    val title: String,
    val instructions: String,
    @SerialName("assignee_id") val assigneeId: String,
    @SerialName("due_at") val dueAt: String? = null,
    val priority: String,
    val status: String,
    val comments: String = "",
)

@Serializable
private data class SupabaseActivityAssignment(
    @SerialName("activity_id") val activityId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class SupabaseMeeting(
    val id: String,
    val title: String,
    val purpose: String,
    @SerialName("meeting_date") val meetingDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("ministry_id") val ministryId: String? = null,
    val location: String,
    val status: String,
)

@Serializable
private data class SupabaseMeetingParticipant(
    @SerialName("meeting_id") val meetingId: String,
    @SerialName("user_id") val userId: String,
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
    @SerialName("activity_id") val activityId: String,
    @SerialName("director_id") val directorId: String,
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
private data class ProjectInsert(
    val id: String,
    val name: String,
    val objective: String,
    val description: String,
    @SerialName("leader_id") val leaderId: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("due_date") val dueDate: String,
    val budget: Long?,
    @SerialName("budget_executed") val budgetExecuted: Long?,
    val status: String,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class ProjectUpdatePayload(
    val name: String,
    val objective: String,
    val description: String,
    @SerialName("leader_id") val leaderId: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("due_date") val dueDate: String,
    val budget: Long?,
    @SerialName("budget_executed") val budgetExecuted: Long?,
    val status: String,
)

@Serializable
private data class ActivityInsert(
    val id: String,
    val title: String,
    val description: String,
    @SerialName("activity_date") val activityDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("ministry_id") val ministryId: String?,
    @SerialName("activity_type") val activityType: String,
    val kind: String,
    val location: String,
    val category: String,
    @SerialName("project_id") val projectId: String?,
    @SerialName("worship_leader_id") val worshipLeaderId: String?,
    @SerialName("preacher_id") val preacherId: String?,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class ActivityStatusUpdate(val status: String)

@Serializable
private data class ActivityAssignmentInsert(
    @SerialName("activity_id") val activityId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("assigned_by") val assignedBy: String,
)

@Serializable
private data class MeetingInsert(
    val id: String,
    val title: String,
    val purpose: String,
    @SerialName("meeting_date") val meetingDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val location: String,
    @SerialName("ministry_id") val ministryId: String?,
    @SerialName("created_by") val createdBy: String,
)

@Serializable
private data class MeetingParticipantInsert(
    @SerialName("meeting_id") val meetingId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class TaskStatusUpdate(
    val status: String,
)

@Serializable
private data class LegacyTaskStatusUpdate(
    @SerialName("task_status") val taskStatus: String,
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
private data class MinistryUpdate(
    val name: String,
    val description: String,
)

@Serializable
private data class MinistryActiveUpdate(
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
private data class MinistryInsert(
    val id: String,
    val name: String,
    val description: String,
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
private data class MembershipLeaderUpdate(
    @SerialName("is_leader") val isLeader: Boolean,
)

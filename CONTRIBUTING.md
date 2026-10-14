# Project requirements and working rules

This is the native Android client for the CMAA church-management platform. The
corrected requirements document dated 2026-09-24 is the functional source of
truth. The Android client and the shared Supabase backend must implement the
same rules; do not treat a client-side check as a substitute for database RLS.

## Product requirements

- **Roles:** Pastor/Administrator, Ministry Leader, and Normal Leader. A user may
  belong to none, one, or several ministries; a ministry leader manages only
  their ministry. A normal leader may update only items assigned to them.
- **Ministries:** Pastors manage ministries and memberships. Alabanza has extra
  worship and rehearsal features. Cleaning (Aseo) is an activity type, not a
  ministry; Pastor is a role, not a ministry.
- **Projects:** Institutional and church-wide only, never owned by a ministry.
  Only the Pastor/Administrator creates, edits, or cancels them. Store name,
  objective, description, responsible user, start and estimated end dates,
  optional estimated/executed budgets, automatically calculated progress, and
  status.
- **Activities:** Each has a type, classification, title, description, date/time,
  location, responsible users, and status. Classification is project,
  ministry, or individual; associate the matching project/ministry only when
  applicable. Types are Aseo, Culto, Ensayo, Reunión de líderes, or Libre.
  Libre activities may use any classification. A leader creates activities
  only within their ministry; creation of individual activities is pending
  client validation.
- **Tasks:** Optional children of an activity, never activities masquerading as
  tasks. Store title, instructions, responsible user, deadline, priority
  (high/medium/low), status, and optional comments. Pastor or the responsible
  ministry leader creates/assigns them; an assigned user changes only their
  own task status. A task with a deadline appears in that user's personal
  calendar.
- **Statuses and progress:** Project, activity, and task statuses are To do,
  In progress, Paused, and Completed. Cancellation remains pending client
  validation. Project progress is based on its activities: an activity without
  tasks is 100% only when completed; otherwise its progress is the fraction of
  completed tasks. Project progress is the average of its activities (zero
  when there are none). Recalculate after relevant status changes.
- **Calendar and home:** Provide general and personal calendar views, type
  filters, and activity details including responsible users, invitees, and
  type-specific fields. Personal view includes assigned activities/tasks and
  Culto/Ensayo where the user participates. Home ("Mis pendientes") shows the
  user's dated work and unread notifications.
- **Culto and Alabanza:** Cultos record preacher and worship director; both can
  see the current repertoire even if outside Alabanza. The reusable repertoire
  has ordered songs, per-song key, version link, permitted files, and optional
  informational song leader, plus last-modified time. One repertoire update
  creates one notification for Alabanza members, Pastor, and assigned
  preachers/directors. Song leadership alone is not a task assignment.
  Alabanza leaders maintain the song catalog and create rehearsals; invitees
  see rehearsals in their personal calendars.
- **Meetings:** Keep calendar invitations separate from post-meeting records.
  Pastor records name, date, summary, files/presentations and visibility
  (everyone, a ministry, or selected people). Only selected users see a record.
- **Notifications:** In-app notifications cover assignments, upcoming
  activities, schedule changes, worship assignments/repertoire changes,
  meeting invitations/records, project changes to affected activity
  responsibles, and critical audit events for the Pastor.
- **Security, files, and audit:** Require email/password authentication and
  password recovery. Enforce role/resource access in Supabase RLS. Only PDF,
  JPG/JPEG, and PNG files are allowed; proposed per-file maximum is 5 MB.
  Downloads follow the associated resource's permissions. Pastor and ministry
  leaders may upload/delete within their scope. Record critical changes with
  actor, timestamp, module, and detail; notify the Pastor.

## Decisions still requiring client validation

Do not silently settle these open questions: whether Cancelled is a valid
status; who creates individual activities; whether ministry leaders can call
their own meetings; whether meeting presentations require PPTX; whether normal
leaders can reassign tasks; whether task completion needs approval; global
storage quota and behavior when full; per-song file/link limits; and project
progress formula (the formula above is proposed). MVP notifications are
in-app; email/WhatsApp notifications are out of scope, but password-recovery
email is in scope. The proposed password policy is at least 8 characters with
one letter and one number. Notification latency, the 2-second dashboard and
calendar target, and exact global-storage quota are not yet validated.

## Implementation rules

- Before a feature change, compare Android code, shared Supabase schema/RLS,
  and any affected web client. Prefer additive migrations and preserve
  compatibility with existing data and clients.
- Work through requirement gaps one at a time. For each, update every affected
  layer, add or update focused tests where available, run
  `.\gradlew.bat assembleDebug`, and report migrations that still need
  deployment separately from code changes.
- Keep remote data access in `SupabaseBackend.kt`; never add a service-role or
  secret key to the mobile app. Surface operation failures; do not report
  success when persistence fails.

# Feature: Staging backend migration

## Objective
Run the Android app exclusively against the CMMA staging Supabase project
(`dmdysvqailticvodbmyl`) and remove every reference to the old project
(`sbvgcnahuunjnczecmpt`).

## Problem
The app was built against an English schema in a separate Supabase project
(`profiles`, `ministries`, `projects`, `activities`, `tasks`, `meetings`,
`worship_*`, `notifications`, RPC `admin_list_users`, function `admin-users`).
Staging belongs to the CMAA web app and uses a Spanish schema (`usuario`,
`ministerio`, `miembro_ministerio`, `proyecto`, `actividad`, `tarea`, `cancion`,
`repertorio`, `reunion`, `archivo`, `notificacion`, function `create-user`).
Switching the URL alone breaks every query.

## Why
Both apps serve the same church domain; staging is the shared source of truth.
Production (`ezcppnxvpgzmxhzlvvla`) must never be used from this work.

## Scope
- Rewrite the data layer (`SupabaseBackend.kt`, `ChurchData.kt`) and the UI that
  depends on removed concepts to match the staging schema, RPCs, RLS and storage.
- Remove old migrations, the `admin-users` function and old project references;
  the schema is owned by the CMAA repo.
- Branch `feat/staging-backend` in worktree `iglesia-gestion-android-staging`.

## Constraints
- Only staging. Never query or configure production.
- Schema changes are out of scope: the app adapts to staging, not the reverse.
- Use RPCs where staging requires them (`crear_actividad`, `set_usuario_ministerios`,
  `listar_responsables_actividad`, `editar_*`, `eliminar_*`).
- Owner columns (`created_by`, `registrado_por`, `subido_por`, `modificado_por`,
  `autor_id`) must equal `auth.uid()`.
- Files go to bucket `archivos` at `<owner_type>/<owner_id>/<file>`.

## Known schema gaps (accepted, follow staging)
- No last sign-in, no admin password reset, no hard delete of users/ministries.
- No standalone tasks (a `tarea` always belongs to an `actividad`), no `cancelled` status.
- Meetings become `actividad` of type `REUNION_LIDERES` + `convocatoria`.
- Repertoire (`repertorio`) is not tied to a culto or ministry.
- Pastor-only writes: proyecto, reunion/visibilidad_reunion, detalle_culto,
  convocatoria, ministerio, miembro_ministerio.
- Notifications are created only by DB triggers.

## TDD
- Mode: strict (source: global CLAUDE.md "Strict TDD Mode: enabled").
- Runner: none existed in the repo. Pure mapping logic (enums, roles, storage
  paths, file types) is covered with JUnit via `./gradlew testDebugUnitTest`.
  Network/UI code is verified by `./gradlew assembleDebug` and manual checks
  against staging.
- JDK: `C:\Program Files\Java\jdk-21` (Android Studio JBR is JDK 25, unsupported by Gradle 8.9).
- Gradle flags (low resource usage, user request): `--max-workers=1 -Dorg.gradle.jvmargs=-Xmx1g --no-daemon`.

## Delivery
- RDD: off (global), no native review.
- Forecast: well above 400 authored changed lines (data layer rewrite).
- Strategy: ask-on-risk; chain strategy `feature-branch-chain` (user choice):
  slice PRs target `feat/staging-backend`, one final PR to `main`.
- Slices: (recorded as PRs are planned)

## Route log
- T1+T2: delegated direct (writer trigger: 2+ non-trivial files).
- T3+T4: delegated direct (writer trigger: 5 non-trivial files).
- T5-T7: delegated direct (writer trigger: 8+ non-trivial files).

## Tasks
- [x] T1 Config and cleanup: staging URL/key in `supabase.properties`, remove
  `supabase/migrations`, `supabase/functions/admin-users`, `supabase/config.toml`
  old ref, update README/CONTRIBUTING/PUBLICACION_PLAY_STORE/supabase README
  to point at CMAA for schema. Add `.mcp.json` (staging only).
- [x] T2 Mapping foundation: JUnit setup + pure mappers (roles, status, activity
  type, classification, priority, visibility scope, file type, storage path) with tests.
- [x] T3 Auth and profile: `usuario` profile load, `estado` check, leader
  detection via `miembro_ministerio.es_lider`, forced password change when
  `debe_cambiar_contrasena`.
- [x] T4 Ministries, members and user admin: `ministerio`, `miembro_ministerio`,
  `set_usuario_ministerios`, `create-user` contract (temporary password shown
  once), `usuario` updates; drop icon/color/last sign-in/admin password.
- [x] T5 Projects: `proyecto` mapping, required fields, computed progress.
- [x] T6 Activities and agenda: `crear_actividad`, `listar_responsables_actividad`,
  status updates, `detalle_culto`, meetings as `REUNION_LIDERES` + `convocatoria`.
- [x] T7 Tasks and comments: `tarea` tied to `actividad`, `comentario_tarea`, RPC edit/delete.
- [ ] T8 Meeting records and files: `reunion`, `visibilidad_reunion`, `archivo` in bucket `archivos`.
- [ ] T9 Worship: `cancion`, song files, `repertorio`/`repertorio_cancion`.
- [ ] T10 Notifications: `notificacion` read/mark read; remove client-side inserts.
- [ ] T11 Final cleanup and full build; manual check plan against staging.

## Acceptance criteria
- No reference to `sbvgcnahuunjnczecmpt` or old tables/RPCs/functions remains.
- `./gradlew testDebugUnitTest assembleDebug` passes.
- Every screen reads/writes staging tables respecting RLS (UI hides pastor-only actions).

## Progress
- Worktree and branch created from `main` (bff1234).
- Mapping report gathered (Engram `discovery/staging-schema-gap`).
- T1 done: f3e9998 (grep: old project id only in this doc).
- T2 done: dee74dd (RED: compile failure on unresolved `StagingMappings`;
  GREEN: 15 tests, 0 failures; `assembleDebug` OK).
- Mapper fallbacks: cancelled -> EN_PAUSA, unknown status -> POR_HACER,
  unknown role -> LIDER_NORMAL, unknown visibility -> Selected, unknown type -> LIBRE.
  Mappers take app internal values, not DB strings.

- T3 done: 9284edc (RED: unresolved `StagingAccounts`; GREEN: 5+15 tests, `assembleDebug` OK).
  Inactive users are signed out; forced `ChangePasswordForm` when `debe_cambiar_contrasena`.
- T4 done: 876f3af (5+15 tests, `assembleDebug` OK; compile-only for network/UI).
  `set_usuario_ministerios` keeps `es_lider` on kept rows (definition read on staging).
  create-user temporary password shown once; role/phone/leader applied as a second step.
  Removed last sign-in, admin set-password, `admin-users` client.
- T5 done: 0590806 (RED: unresolved `ProjectProgress`; GREEN: 4 tests). Client-side progress.
- T6 done: 81d637e (RED: unresolved `StagingActivities`; GREEN: 8+15 tests).
  `crear_actividad` does not set `fin` (follow-up UPDATE). `p_alcance` = ESTA | SIGUIENTES.
  Classification `General` -> INDIVIDUAL (PROYECTO requires proyecto_id by check).
  Meetings = REUNION_LIDERES + `convocatoria` (pastor only); a ministry leader's
  meeting is MINISTERIAL without convocatoria rows.
- T7 done: 532ad71 (RED: unresolved `StagingTasks`; GREEN: 37 tests total, `assembleDebug` OK).
  Comments in `comentario_tarea`. Edit/delete RPCs not wired (no UI for them).
- Known runtime gap until T8-T10: snapshot still reads worship, meeting record and notification tables.

## Next step
T8-T10 (meeting records/files, worship, notifications).

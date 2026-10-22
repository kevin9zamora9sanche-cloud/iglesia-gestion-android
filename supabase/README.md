# Configuración inicial de Supabase

Esta migración configura la autenticación, el perfil básico y los roles que exige
el MVP: Pastor/Administrador, Líder de ministerio y Líder normal. Las cuentas se
crean en Supabase Auth y el perfil asociado recibe por defecto el rol `leader`;
los metadatos enviados por el cliente nunca pueden asignar un rol privilegiado.

## Configurar el proyecto y aplicar migraciones

Este repositorio está enlazado al proyecto Supabase `sbvgcnahuunjnczecmpt` por
medio de `config.toml`. En Windows se puede ejecutar la CLI con `npx` usando
Node.js 20 o posterior, sin instalarla globalmente:

```powershell
npx.cmd --yes supabase --version
npx.cmd --yes supabase login
npx.cmd --yes supabase link --project-ref sbvgcnahuunjnczecmpt
```

`supabase login` solicita un Personal Access Token de Supabase. Créalo desde
**Account → Access Tokens** y escríbelo únicamente en la terminal local.
`supabase link` puede solicitar la contraseña de la base de datos; ingrésala
solo en esa terminal. No agregues ninguna de esas credenciales a archivos del
repositorio ni las compartas por chat.

Antes de aplicar cambios, revisa el historial remoto y el plan:

```powershell
npx.cmd --yes supabase migration list --linked
npx.cmd --yes supabase db push --linked --dry-run
```

Si la base está vacía, aplica las migraciones con:

```powershell
npx.cmd --yes supabase db push --linked
```

Si ya se ejecutaron migraciones manualmente desde SQL Editor, detente si la CLI
las muestra pendientes. Primero compara el esquema remoto con cada archivo y
reconcilia el historial de migraciones; no uses `migration repair`,
`--include-all` ni `db reset` sin comprobar el estado y tener un respaldo. No
uses `db reset` contra producción.

Las migraciones se aplican por timestamp y en este orden:

1. `migrations/20261009100000_initial_auth_and_rbac.sql`
2. `migrations/20261009120000_management_modules.sql`
3. `migrations/20261009123000_restrict_internal_trigger_functions.sql`
4. `migrations/20261009124500_restrict_assignee_task_updates.sql`
5. `migrations/20261009130000_pastor_profile_management.sql`
6. `migrations/20261014100000_align_project_permissions_and_fields.sql`
7. `migrations/20261014110000_separate_tasks_from_activities.sql`
8. `migrations/20261014120000_enable_ministry_responsible_selection.sql`
9. `migrations/20261014130000_backfill_unambiguous_legacy_tasks.sql`
10. `migrations/20261022100000_align_status_progress_worship_and_notifications.sql`
11. `migrations/20261022110000_meeting_records_and_files.sql`

La migración 11 crea los registros posteriores a reuniones (`meeting_records`),
separados de las convocatorias, con visibilidad para toda la iglesia, un
ministerio o personas seleccionadas; sus archivos (`meeting_record_files`) y el
bucket privado `meeting-records` (PDF, JPG o PNG, máximo 5 MB). Las descargas
siguen los permisos del registro y solo el Pastor crea, adjunta o elimina.

La migración 10 agrega los estados de actividad En curso/En pausa, recalcula
automáticamente el avance de los proyectos, permite que predicador y director
vean su Culto, guarda el repertorio de forma atómica (`save_worship_set`) con un
solo aviso por cambio, notifica asignaciones de culto, invitaciones y cambios de
reuniones, cambios de horario y de proyectos, y registra el detalle en la
auditoría avisando al Pastor de las eliminaciones. La app Android la necesita
para cambiar estados de actividades y editar repertorios.

El último backfill solo migra tareas antiguas con actividad padre y una única
persona responsable identificables; conserva sin cambios las ambiguas, las
canceladas o las que no tienen responsable. Revisa los avisos `NOTICE` y las
tareas antiguas restantes tras aplicarlo.

Después de aplicar el esquema:

1. En **Authentication → Providers → Email**, habilita correo/contraseña y
   configura la confirmación de correo. El registro público debe permanecer
   deshabilitado; las cuentas deben crearse por administración.
2. Crea las cuentas de usuarios desde **Authentication → Users**. El trigger
   crea un perfil con rol `leader` automáticamente.
3. Para designar al Pastor inicial, cambia el rol de su perfil desde SQL Editor
   después de verificar la cuenta:

   ```sql
   update public.profiles
   set role = 'pastor'
   where id = (
     select id from auth.users where email = 'correo-verificado@ejemplo.org'
   );
   ```

   No se debe permitir que una persona se autodesigne Pastor ni usar la clave
   `service_role` en una aplicación cliente.
4. Los ministerios y membresías se administran desde la aplicación como Pastor.

## Módulos de gestión

`20261009120000_management_modules.sql` crea tablas protegidas con RLS para
proyectos, agenda y tareas, asignaciones, repertorios, reuniones, notificaciones,
archivos y auditoría; también crea el bucket privado `church-resources` con un
límite de 5 MB y formatos PDF, JPG y PNG. `20261009123000_restrict_internal_trigger_functions.sql`
retira permisos de ejecución directa sobre las funciones internas de los triggers.

Los Pastores administran todos los módulos. Los Líderes de ministerio pueden
gestionar los registros de los ministerios donde tienen membresía de liderazgo.
Los Líderes normales consultan lo que les corresponde y actualizan únicamente el
estado de las tareas que les fueron asignadas. Un trigger valida la fila
completa y rechaza cambios de cualquier otra columna para esos usuarios. Las
cuentas inactivas no pueden usar los módulos. `20261009130000_pastor_profile_management.sql`
añade una operación protegida para que un Pastor activo pueda cambiar nombres,
teléfonos, roles y estado de los perfiles; impide quedarse sin ningún Pastor
activo. Las membresías a ministerios se gestionan desde la sección Ministerios
y sus políticas RLS reservan los cambios de membresía al Pastor.

Los cambios más recientes hacen los proyectos institucionales y reservan su
gestión al Pastor, separan las tareas de las actividades, permiten que los
miembros vean compañeros activos de sus ministerios para asignarlos, y migran
conservadoramente las tareas históricas. No desactives RLS ni publiques tablas
nuevas sin políticas.

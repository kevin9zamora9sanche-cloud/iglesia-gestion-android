begin;

do $$ 
begin
  if not exists (select 1 from pg_type where typname = 'project_status') then
    create type public.project_status as enum (
      'pending',
      'in_progress',
      'completed',
      'cancelled'
    );
  end if;
end $$;

do $$ 
begin
  if not exists (select 1 from pg_type where typname = 'activity_kind') then
    create type public.activity_kind as enum (
      'activity',
      'task',
      'meeting'
    );
  end if;
end $$;

do $$ 
begin
  if not exists (select 1 from pg_type where typname = 'activity_type') then
    create type public.activity_type as enum (
      'general',
      'ministry',
      'personal'
    );
  end if;
end $$;

do $$ 
begin
  if not exists (select 1 from pg_type where typname = 'event_status') then
    create type public.event_status as enum (
      'scheduled',
      'completed',
      'cancelled'
    );
  end if;
end $$;

do $$ 
begin
  if not exists (select 1 from pg_type where typname = 'task_status') then
    create type public.task_status as enum (
      'pending',
      'in_progress',
      'completed',
      'cancelled'
    );
  end if;
end $$;

do $$ 
begin
  if not exists (select 1 from pg_type where typname = 'task_priority') then
    create type public.task_priority as enum (
      'high',
      'medium',
      'low'
    );
  end if;
end $$;

do $$ 
begin
  if not exists (select 1 from pg_type where typname = 'notification_type') then
    create type public.notification_type as enum (
      'assignment',
      'schedule',
      'system'
    );
  end if;
end $$;

CREATE TABLE IF NOT EXISTS public.projects (
  id uuid primary key default extensions.gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  description text not null default '',
  ministry_id uuid references public.ministries(id) on delete restrict,
  leader_id uuid not null references public.profiles(id) on delete restrict,
  start_date date not null,
  due_date date not null,
  budget numeric(14, 2) check (budget is null or budget >= 0),
  progress smallint not null default 0 check (progress between 0 and 100),
  status public.project_status not null default 'pending',
  created_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint projects_dates_ordered check (due_date >= start_date)
);

CREATE INDEX IF NOT EXISTS projects_ministry_status_idx
  on public.projects(ministry_id, status);

CREATE INDEX IF NOT EXISTS projects_leader_id_idx
  on public.projects(leader_id);

CREATE TABLE IF NOT EXISTS public.activities (
  id uuid primary key default extensions.gen_random_uuid(),
  title text not null check (length(trim(title)) > 0),
  description text not null default '',
  activity_date date not null,
  start_time time not null,
  end_time time not null,
  ministry_id uuid references public.ministries(id) on delete restrict,
  activity_type public.activity_type not null default 'general',
  kind public.activity_kind not null default 'activity',
  location text not null default '',
  status public.event_status not null default 'scheduled',
  category text not null default '',
  priority public.task_priority,
  task_status public.task_status,
  project_id uuid references public.projects(id) on delete set null,
  worship_leader_id uuid references public.profiles(id) on delete set null,
  preacher_id uuid references public.profiles(id) on delete set null,
  created_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint activities_times_ordered check (end_time > start_time),
  constraint activities_ministry_type check (
    activity_type <> 'ministry' or ministry_id is not null
  ),
  constraint activities_task_fields check (
    (kind = 'task' and priority is not null and task_status is not null)
    or (kind <> 'task' and priority is null and task_status is null)
  ),
  constraint activities_cult_leaders check (
    category <> 'Culto'
    or kind <> 'activity'
    or (worship_leader_id is not null and preacher_id is not null)
  )
);

CREATE INDEX IF NOT EXISTS activities_date_time_idx
  on public.activities(activity_date, start_time);

CREATE INDEX IF NOT EXISTS activities_ministry_date_idx
  on public.activities(ministry_id, activity_date);

CREATE INDEX IF NOT EXISTS activities_project_id_idx
  on public.activities(project_id);

CREATE TABLE IF NOT EXISTS public.activity_assignments (
  activity_id uuid not null references public.activities(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  assigned_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  primary key (activity_id, user_id)
);

CREATE INDEX IF NOT EXISTS activity_assignments_user_activity_idx
  on public.activity_assignments(user_id, activity_id);

CREATE TABLE IF NOT EXISTS public.worship_songs (
  id uuid primary key default extensions.gen_random_uuid(),
  ministry_id uuid not null references public.ministries(id) on delete cascade,
  title text not null check (length(trim(title)) > 0),
  artist text not null default '',
  default_key text not null default 'C',
  reference_url text not null default '',
  resource_name text,
  created_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, ministry_id)
);

CREATE INDEX IF NOT EXISTS worship_songs_ministry_title_idx
  on public.worship_songs(ministry_id, title);

CREATE TABLE IF NOT EXISTS public.worship_sets (
  id uuid primary key default extensions.gen_random_uuid(),
  activity_id uuid not null unique references public.activities(id) on delete cascade,
  ministry_id uuid not null references public.ministries(id) on delete cascade,
  director_id uuid not null references public.profiles(id) on delete restrict,
  updated_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, ministry_id)
);

CREATE INDEX IF NOT EXISTS worship_sets_ministry_id_idx
  on public.worship_sets(ministry_id);

CREATE TABLE IF NOT EXISTS public.worship_set_songs (
  set_id uuid not null,
  ministry_id uuid not null,
  song_id uuid not null,
  position smallint not null check (position > 0),
  song_key text not null default 'C',
  lead_id uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  primary key (set_id, position),
  foreign key (set_id, ministry_id)
    references public.worship_sets(id, ministry_id) on delete cascade,
  foreign key (song_id, ministry_id)
    references public.worship_songs(id, ministry_id) on delete restrict
);

CREATE TABLE IF NOT EXISTS public.meetings (
  id uuid primary key default extensions.gen_random_uuid(),
  title text not null check (length(trim(title)) > 0),
  purpose text not null default '',
  agenda text not null default '',
  minutes text not null default '',
  meeting_date date not null,
  start_time time not null,
  end_time time not null,
  location text not null default '',
  ministry_id uuid references public.ministries(id) on delete restrict,
  status public.event_status not null default 'scheduled',
  created_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint meetings_times_ordered check (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS meetings_ministry_date_idx
  on public.meetings(ministry_id, meeting_date);

CREATE TABLE IF NOT EXISTS public.meeting_participants (
  meeting_id uuid not null references public.meetings(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (meeting_id, user_id)
);

CREATE INDEX IF NOT EXISTS meeting_participants_user_id_idx
  on public.meeting_participants(user_id, meeting_id);

CREATE TABLE IF NOT EXISTS public.notifications (
  id uuid primary key default extensions.gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  message text not null check (length(trim(message)) > 0),
  type public.notification_type not null,
  created_at timestamptz not null default now(),
  read boolean not null default false
);

CREATE INDEX IF NOT EXISTS notifications_user_unread_idx
  on public.notifications(user_id, created_at desc)
  where not read;

CREATE TABLE IF NOT EXISTS public.church_files (
  id uuid primary key default extensions.gen_random_uuid(),
  ministry_id uuid not null references public.ministries(id) on delete cascade,
  uploaded_by uuid not null default auth.uid() references public.profiles(id),
  file_name text not null check (length(trim(file_name)) > 0),
  content_type text not null check (
    content_type in ('application/pdf', 'image/jpeg', 'image/png')
  ),
  size_bytes bigint not null check (size_bytes between 1 and 5242880),
  object_path text not null unique,
  created_at timestamptz not null default now(),
  constraint church_files_storage_path check (
    object_path = ministry_id::text || '/' || id::text
  )
);

CREATE INDEX IF NOT EXISTS church_files_ministry_created_idx
  on public.church_files(ministry_id, created_at desc);

CREATE TABLE IF NOT EXISTS public.audit_logs (
  id bigint generated always as identity primary key,
  actor_id uuid references public.profiles(id) on delete set null,
  action text not null check (action in ('INSERT', 'UPDATE', 'DELETE')),
  table_name text not null,
  record_id uuid not null,
  occurred_at timestamptz not null default now()
);

CREATE INDEX IF NOT EXISTS audit_logs_occurred_at_idx
  on public.audit_logs(occurred_at desc);

CREATE OR REPLACE FUNCTION public.is_active_user()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.is_active
  );
$$;

CREATE OR REPLACE FUNCTION public.is_ministry_member(target_ministry_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.ministry_memberships mm
    join public.profiles p on p.id = mm.user_id
    where mm.ministry_id = target_ministry_id
      and mm.user_id = (select auth.uid())
      and p.is_active
  );
$$;

CREATE OR REPLACE FUNCTION public.is_activity_assignee(target_activity_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.activity_assignments aa
    where aa.activity_id = target_activity_id
      and aa.user_id = (select auth.uid())
  );
$$;

CREATE OR REPLACE FUNCTION public.can_manage_activity(target_activity_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.activities a
    where a.id = target_activity_id
      and (
        public.is_pastor()
        or (
          a.ministry_id is not null
          and public.can_manage_ministry(a.ministry_id)
        )
      )
  );
$$;

CREATE OR REPLACE FUNCTION public.can_manage_project(target_project_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.projects p
    where p.id = target_project_id
      and (
        public.is_pastor()
        or (
          p.ministry_id is not null
          and public.can_manage_ministry(p.ministry_id)
        )
      )
  );
$$;

CREATE OR REPLACE FUNCTION public.is_meeting_participant(target_meeting_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.meeting_participants mp
    where mp.meeting_id = target_meeting_id
      and mp.user_id = (select auth.uid())
  );
$$;

CREATE OR REPLACE FUNCTION public.can_manage_meeting(target_meeting_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.meetings m
    where m.id = target_meeting_id
      and (
        public.is_pastor()
        or (
          m.ministry_id is not null
          and public.can_manage_ministry(m.ministry_id)
        )
      )
  );
$$;

CREATE OR REPLACE FUNCTION public.can_assign_active_user(
  target_user_id uuid,
  target_activity_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.profiles p
    where p.id = target_user_id
      and p.is_active
      and public.can_manage_activity(target_activity_id)
  );
$$;

CREATE OR REPLACE FUNCTION public.can_add_meeting_participant(
  target_user_id uuid,
  target_meeting_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.profiles p
    where p.id = target_user_id
      and p.is_active
      and public.can_manage_meeting(target_meeting_id)
  );
$$;

CREATE OR REPLACE FUNCTION public.create_assignment_notification()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  activity_title text;
begin
  select a.title
  into activity_title
  from public.activities a
  where a.id = new.activity_id;

  insert into public.notifications (user_id, message, type)
  values (
    new.user_id,
    'Se te asignó "' || coalesce(activity_title, 'una actividad') || '".',
    'assignment'
  );

  return new;
end;
$$;

DROP TRIGGER IF EXISTS activity_assignment_notification ON public.activity_assignments;
create trigger activity_assignment_notification
  after insert on public.activity_assignments
  for each row execute function public.create_assignment_notification();

CREATE OR REPLACE FUNCTION public.log_management_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  changed_id uuid;
begin
  if tg_op = 'DELETE' then
    changed_id := old.id;
  else
    changed_id := new.id;
  end if;

  insert into public.audit_logs (actor_id, action, table_name, record_id)
  values ((select auth.uid()), tg_op, tg_table_name, changed_id);

  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$$;

DROP TRIGGER IF EXISTS projects_audit ON public.projects;
create trigger projects_audit
  after insert or update or delete on public.projects
  for each row execute function public.log_management_change();

DROP TRIGGER IF EXISTS activities_audit ON public.activities;
create trigger activities_audit
  after insert or update or delete on public.activities
  for each row execute function public.log_management_change();

DROP TRIGGER IF EXISTS worship_songs_audit ON public.worship_songs;
create trigger worship_songs_audit
  after insert or update or delete on public.worship_songs
  for each row execute function public.log_management_change();

DROP TRIGGER IF EXISTS worship_sets_audit ON public.worship_sets;
create trigger worship_sets_audit
  after insert or update or delete on public.worship_sets
  for each row execute function public.log_management_change();

DROP TRIGGER IF EXISTS meetings_audit ON public.meetings;
create trigger meetings_audit
  after insert or update or delete on public.meetings
  for each row execute function public.log_management_change();

DROP TRIGGER IF EXISTS projects_set_updated_at ON public.projects;
create trigger projects_set_updated_at
  before update on public.projects
  for each row execute function public.set_updated_at();

DROP TRIGGER IF EXISTS activities_set_updated_at ON public.activities;
create trigger activities_set_updated_at
  before update on public.activities
  for each row execute function public.set_updated_at();

DROP TRIGGER IF EXISTS worship_songs_set_updated_at ON public.worship_songs;
create trigger worship_songs_set_updated_at
  before update on public.worship_songs
  for each row execute function public.set_updated_at();

DROP TRIGGER IF EXISTS worship_sets_set_updated_at ON public.worship_sets;
create trigger worship_sets_set_updated_at
  before update on public.worship_sets
  for each row execute function public.set_updated_at();

DROP TRIGGER IF EXISTS meetings_set_updated_at ON public.meetings;
create trigger meetings_set_updated_at
  before update on public.meetings
  for each row execute function public.set_updated_at();

alter table public.projects enable row level security;
alter table public.activities enable row level security;
alter table public.activity_assignments enable row level security;
alter table public.worship_songs enable row level security;
alter table public.worship_sets enable row level security;
alter table public.worship_set_songs enable row level security;
alter table public.meetings enable row level security;
alter table public.meeting_participants enable row level security;
alter table public.notifications enable row level security;
alter table public.church_files enable row level security;
alter table public.audit_logs enable row level security;

revoke all on public.projects, public.activities, public.activity_assignments,
  public.worship_songs, public.worship_sets, public.worship_set_songs,
  public.meetings, public.meeting_participants, public.notifications,
  public.church_files, public.audit_logs from anon, authenticated;

revoke all on function public.is_active_user() from public, anon;
revoke all on function public.is_ministry_member(uuid) from public, anon;
revoke all on function public.is_activity_assignee(uuid) from public, anon;
revoke all on function public.can_manage_activity(uuid) from public, anon;
revoke all on function public.can_manage_project(uuid) from public, anon;
revoke all on function public.is_meeting_participant(uuid) from public, anon;
revoke all on function public.can_manage_meeting(uuid) from public, anon;
revoke all on function public.can_assign_active_user(uuid, uuid) from public, anon;
revoke all on function public.can_add_meeting_participant(uuid, uuid) from public, anon;
revoke all on function public.create_assignment_notification()
  from public, anon, authenticated;
revoke all on function public.log_management_change()
  from public, anon, authenticated;

grant execute on function public.is_active_user() to authenticated;
grant execute on function public.is_ministry_member(uuid) to authenticated;
grant execute on function public.is_activity_assignee(uuid) to authenticated;
grant execute on function public.can_manage_activity(uuid) to authenticated;
grant execute on function public.can_manage_project(uuid) to authenticated;
grant execute on function public.is_meeting_participant(uuid) to authenticated;
grant execute on function public.can_manage_meeting(uuid) to authenticated;
grant execute on function public.can_assign_active_user(uuid, uuid) to authenticated;
grant execute on function public.can_add_meeting_participant(uuid, uuid) to authenticated;

grant select on public.projects, public.activities, public.activity_assignments,
  public.worship_songs, public.worship_sets, public.worship_set_songs,
  public.meetings, public.meeting_participants, public.notifications,
  public.church_files to authenticated;
grant select on public.audit_logs to authenticated;

grant insert, delete on public.projects, public.activities,
  public.activity_assignments, public.worship_songs, public.worship_sets,
  public.worship_set_songs, public.meetings, public.meeting_participants,
  public.church_files to authenticated;

grant update (
  name, description, ministry_id, leader_id, start_date, due_date, budget,
  progress, status
) on public.projects to authenticated;
grant update (
  ministry_id, title, artist, default_key, reference_url, resource_name
) on public.worship_songs to authenticated;
grant update (
  director_id, updated_by
) on public.worship_sets to authenticated;
grant update (position, song_key, lead_id)
  on public.worship_set_songs to authenticated;
grant update (
  title, purpose, agenda, minutes, meeting_date, start_time, end_time,
  location, ministry_id, status
) on public.meetings to authenticated;
grant update (ministry_id, file_name, content_type, size_bytes, object_path)
  on public.church_files to authenticated;
grant update (
  title, description, activity_date, start_time, end_time, ministry_id,
  activity_type, kind, location, status, category, priority, task_status,
  project_id, worship_leader_id, preacher_id
) on public.activities to authenticated;
grant update (read) on public.notifications to authenticated;

DROP POLICY IF EXISTS "Active users can view their projects" ON public.projects;
create policy "Active users can view their projects"
  on public.projects for select to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or leader_id = (select auth.uid())
      or (
        ministry_id is not null
        and (select public.is_ministry_member(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can create projects" ON public.projects;
create policy "Pastors and ministry leaders can create projects"
  on public.projects for insert to authenticated
  with check (
    (select public.is_active_user())
    and created_by = (select auth.uid())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can update projects" ON public.projects;
create policy "Pastors and ministry leaders can update projects"
  on public.projects for update to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  )
  with check (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can delete projects" ON public.projects;
create policy "Pastors and ministry leaders can delete projects"
  on public.projects for delete to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

drop policy if exists "Authenticated users can view active ministries"
  on public.ministries;

DROP POLICY IF EXISTS "Active users can view active ministries" ON public.ministries;
create policy "Active users can view active ministries"
  on public.ministries for select to authenticated
  using (
    (select public.is_active_user())
    and (is_active or (select public.is_pastor()))
  );

drop policy if exists "Users can view their ministry memberships; pastors can view all"
  on public.ministry_memberships;

DROP POLICY IF EXISTS "Active users can view relevant ministry memberships" ON public.ministry_memberships;
create policy "Active users can view relevant ministry memberships"
  on public.ministry_memberships for select to authenticated
  using (
    (select public.is_active_user())
    and (
      user_id = (select auth.uid())
      or (select public.is_pastor())
      or (select public.can_manage_ministry(ministry_id))
    )
  );

DROP POLICY IF EXISTS "Active users can view relevant activities" ON public.activities;
create policy "Active users can view relevant activities"
  on public.activities for select to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (select public.is_activity_assignee(id))
      or (
        activity_type <> 'personal'
        and (
          ministry_id is null
          or (select public.is_ministry_member(ministry_id))
        )
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can create activities" ON public.activities;
create policy "Pastors and ministry leaders can create activities"
  on public.activities for insert to authenticated
  with check (
    (select public.is_active_user())
    and created_by = (select auth.uid())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can update activities" ON public.activities;
create policy "Pastors and ministry leaders can update activities"
  on public.activities for update to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  )
  with check (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Assigned users can update task status" ON public.activities;
create policy "Assigned users can update task status"
  on public.activities for update to authenticated
  using (
    (select public.is_active_user())
    and kind = 'task'
    and (select public.is_activity_assignee(id))
  )
  with check (
    (select public.is_active_user())
    and kind = 'task'
    and (select public.is_activity_assignee(id))
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can delete activities" ON public.activities;
create policy "Pastors and ministry leaders can delete activities"
  on public.activities for delete to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Users can view their activity assignments" ON public.activity_assignments;
create policy "Users can view their activity assignments"
  on public.activity_assignments for select to authenticated
  using (
    (select public.is_active_user())
    and (
      user_id = (select auth.uid())
      or (select public.can_manage_activity(activity_id))
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can assign activities" ON public.activity_assignments;
create policy "Pastors and ministry leaders can assign activities"
  on public.activity_assignments for insert to authenticated
  with check (
    (select public.is_active_user())
    and assigned_by = (select auth.uid())
    and (select public.can_assign_active_user(user_id, activity_id))
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can remove activity assignments" ON public.activity_assignments;
create policy "Pastors and ministry leaders can remove activity assignments"
  on public.activity_assignments for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_activity(activity_id))
  );

DROP POLICY IF EXISTS "Active users can view song catalogs" ON public.worship_songs;
create policy "Active users can view song catalogs"
  on public.worship_songs for select to authenticated
  using ((select public.is_active_user()));

DROP POLICY IF EXISTS "Worship ministry leaders can add songs" ON public.worship_songs;
create policy "Worship ministry leaders can add songs"
  on public.worship_songs for insert to authenticated
  with check (
    (select public.is_active_user())
    and created_by = (select auth.uid())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Worship ministry leaders can update songs" ON public.worship_songs;
create policy "Worship ministry leaders can update songs"
  on public.worship_songs for update to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  )
  with check (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Worship ministry leaders can delete songs" ON public.worship_songs;
create policy "Worship ministry leaders can delete songs"
  on public.worship_songs for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Active users can view worship sets" ON public.worship_sets;
create policy "Active users can view worship sets"
  on public.worship_sets for select to authenticated
  using ((select public.is_active_user()));

DROP POLICY IF EXISTS "Worship ministry leaders can create sets" ON public.worship_sets;
create policy "Worship ministry leaders can create sets"
  on public.worship_sets for insert to authenticated
  with check (
    (select public.is_active_user())
    and updated_by = (select auth.uid())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Worship ministry leaders can update sets" ON public.worship_sets;
create policy "Worship ministry leaders can update sets"
  on public.worship_sets for update to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  )
  with check (
    (select public.is_active_user())
    and updated_by = (select auth.uid())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Worship ministry leaders can delete sets" ON public.worship_sets;
create policy "Worship ministry leaders can delete sets"
  on public.worship_sets for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Active users can view worship set songs" ON public.worship_set_songs;
create policy "Active users can view worship set songs"
  on public.worship_set_songs for select to authenticated
  using ((select public.is_active_user()));

DROP POLICY IF EXISTS "Worship ministry leaders can add set songs" ON public.worship_set_songs;
create policy "Worship ministry leaders can add set songs"
  on public.worship_set_songs for insert to authenticated
  with check (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Worship ministry leaders can update set songs" ON public.worship_set_songs;
create policy "Worship ministry leaders can update set songs"
  on public.worship_set_songs for update to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  )
  with check (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Worship ministry leaders can remove set songs" ON public.worship_set_songs;
create policy "Worship ministry leaders can remove set songs"
  on public.worship_set_songs for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Participants and managers can view meetings" ON public.meetings;
create policy "Participants and managers can view meetings"
  on public.meetings for select to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (select public.is_meeting_participant(id))
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can create meetings" ON public.meetings;
create policy "Pastors and ministry leaders can create meetings"
  on public.meetings for insert to authenticated
  with check (
    (select public.is_active_user())
    and created_by = (select auth.uid())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can update meetings" ON public.meetings;
create policy "Pastors and ministry leaders can update meetings"
  on public.meetings for update to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  )
  with check (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can delete meetings" ON public.meetings;
create policy "Pastors and ministry leaders can delete meetings"
  on public.meetings for delete to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        ministry_id is not null
        and (select public.can_manage_ministry(ministry_id))
      )
    )
  );

DROP POLICY IF EXISTS "Participants and managers can view meeting participants" ON public.meeting_participants;
create policy "Participants and managers can view meeting participants"
  on public.meeting_participants for select to authenticated
  using (
    (select public.is_active_user())
    and (
      user_id = (select auth.uid())
      or (select public.can_manage_meeting(meeting_id))
    )
  );

DROP POLICY IF EXISTS "Meeting managers can add active participants" ON public.meeting_participants;
create policy "Meeting managers can add active participants"
  on public.meeting_participants for insert to authenticated
  with check (
    (select public.is_active_user())
    and (select public.can_add_meeting_participant(user_id, meeting_id))
  );

DROP POLICY IF EXISTS "Meeting managers can remove participants" ON public.meeting_participants;
create policy "Meeting managers can remove participants"
  on public.meeting_participants for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_meeting(meeting_id))
  );

DROP POLICY IF EXISTS "Users can read their notifications" ON public.notifications;
create policy "Users can read their notifications"
  on public.notifications for select to authenticated
  using (
    (select public.is_active_user())
    and user_id = (select auth.uid())
  );

DROP POLICY IF EXISTS "Users can mark their notifications read" ON public.notifications;
create policy "Users can mark their notifications read"
  on public.notifications for update to authenticated
  using (
    (select public.is_active_user())
    and user_id = (select auth.uid())
  )
  with check (
    (select public.is_active_user())
    and user_id = (select auth.uid())
  );

DROP POLICY IF EXISTS "Active users can view ministry files" ON public.church_files;
create policy "Active users can view ministry files"
  on public.church_files for select to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (select public.is_ministry_member(ministry_id))
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can add ministry files" ON public.church_files;
create policy "Pastors and ministry leaders can add ministry files"
  on public.church_files for insert to authenticated
  with check (
    (select public.is_active_user())
    and uploaded_by = (select auth.uid())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can update ministry files" ON public.church_files;
create policy "Pastors and ministry leaders can update ministry files"
  on public.church_files for update to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  )
  with check (
    (select public.is_active_user())
    and uploaded_by = (select auth.uid())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can delete ministry files" ON public.church_files;
create policy "Pastors and ministry leaders can delete ministry files"
  on public.church_files for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Pastors can read audit logs" ON public.audit_logs;
create policy "Pastors can read audit logs"
  on public.audit_logs for select to authenticated
  using ((select public.is_pastor()));

insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'church-resources',
  'church-resources',
  false,
  5242880,
  array['application/pdf', 'image/jpeg', 'image/png']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

DROP POLICY IF EXISTS "Active users can read church resources" ON storage.objects;
create policy "Active users can read church resources"
  on storage.objects for select to authenticated
  using (
    bucket_id = 'church-resources'
    and (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (
        (storage.foldername(name))[1] is not null
        and (select public.is_ministry_member(
          (storage.foldername(name))[1]::uuid
        ))
      )
    )
  );

DROP POLICY IF EXISTS "Ministry leaders can upload church resources" ON storage.objects;
create policy "Ministry leaders can upload church resources"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'church-resources'
    and (select public.is_active_user())
    and (select public.can_manage_ministry((storage.foldername(name))[1]::uuid))
  );

DROP POLICY IF EXISTS "Ministry leaders can delete church resources" ON storage.objects;
create policy "Ministry leaders can delete church resources"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'church-resources'
    and (select public.is_active_user())
    and (select public.can_manage_ministry((storage.foldername(name))[1]::uuid))
  );

commit;

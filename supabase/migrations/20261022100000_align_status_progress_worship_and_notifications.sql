-- Alinea el backend con los requerimientos corregidos:
-- * Estados de actividad Por hacer/En curso/En pausa/Completada.
-- * Avance de proyectos calculado automáticamente a partir de sus actividades.
-- * Predicador y director de alabanza pueden ver su Culto.
-- * Guardado atómico del repertorio con una sola notificación por cambio.
-- * Notificaciones de cultos, reuniones, cambios de horario y proyectos.
-- * Auditoría con detalle y aviso al Pastor de eliminaciones críticas.

-- Los nuevos valores de enum deben confirmarse antes de poder usarse.
alter type public.event_status add value if not exists 'in_progress';
alter type public.event_status add value if not exists 'paused';

begin;

-- Avance de proyectos ---------------------------------------------------------

create or replace function public.recalculate_project_progress(target_project_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  calculated smallint;
begin
  if target_project_id is null then
    return;
  end if;

  select coalesce(round(avg(per_activity.activity_progress)), 0)::smallint
  into calculated
  from (
    select case
      when count(t.id) = 0 then
        case when a.status = 'completed' then 100.0 else 0.0 end
      else
        100.0 * count(t.id) filter (where t.status = 'completed') / count(t.id)
    end as activity_progress
    from public.activities a
    left join public.tasks t on t.activity_id = a.id
    where a.project_id = target_project_id
      and a.kind = 'activity'
    group by a.id, a.status
  ) per_activity;

  update public.projects
  set progress = calculated
  where id = target_project_id
    and progress is distinct from calculated;
end;
$$;

revoke all on function public.recalculate_project_progress(uuid)
  from public, anon, authenticated;

create or replace function public.refresh_project_progress_from_activity()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op <> 'INSERT' then
    perform public.recalculate_project_progress(old.project_id);
  end if;

  if tg_op = 'INSERT'
    or (tg_op = 'UPDATE' and new.project_id is distinct from old.project_id) then
    perform public.recalculate_project_progress(new.project_id);
  end if;

  return null;
end;
$$;

revoke all on function public.refresh_project_progress_from_activity()
  from public, anon, authenticated;

drop trigger if exists activities_refresh_project_progress on public.activities;
create trigger activities_refresh_project_progress
  after insert or delete or update of status, project_id, kind
  on public.activities
  for each row execute function public.refresh_project_progress_from_activity();

create or replace function public.refresh_project_progress_from_task()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op <> 'INSERT' then
    perform public.recalculate_project_progress(
      (select a.project_id from public.activities a where a.id = old.activity_id)
    );
  end if;

  if tg_op = 'INSERT'
    or (tg_op = 'UPDATE' and new.activity_id is distinct from old.activity_id) then
    perform public.recalculate_project_progress(
      (select a.project_id from public.activities a where a.id = new.activity_id)
    );
  end if;

  return null;
end;
$$;

revoke all on function public.refresh_project_progress_from_task()
  from public, anon, authenticated;

drop trigger if exists tasks_refresh_project_progress on public.tasks;
create trigger tasks_refresh_project_progress
  after insert or delete or update of status, activity_id
  on public.tasks
  for each row execute function public.refresh_project_progress_from_task();

do $$
declare
  existing_project record;
begin
  for existing_project in select id from public.projects loop
    perform public.recalculate_project_progress(existing_project.id);
  end loop;
end $$;

-- Visibilidad de cultos para predicador y director ---------------------------

drop policy if exists "Active users can view relevant activities" on public.activities;
create policy "Active users can view relevant activities"
  on public.activities for select to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (select public.is_activity_assignee(id))
      or preacher_id = (select auth.uid())
      or worship_leader_id = (select auth.uid())
      or exists (
        select 1
        from public.tasks t
        where t.activity_id = activities.id
          and t.assignee_id = (select auth.uid())
      )
      or (
        activity_type <> 'personal'
        and (
          ministry_id is null
          or (select public.is_ministry_member(ministry_id))
        )
      )
    )
  );

-- Repertorio -----------------------------------------------------------------

create or replace function public.save_worship_set(
  target_activity_id uuid,
  target_ministry_id uuid,
  target_songs jsonb
)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  culto_director uuid;
  saved_set_id uuid;
  saved_set_ministry_id uuid;
begin
  if not (select public.can_manage_ministry(target_ministry_id)) then
    raise exception 'Only worship ministry leaders may edit the repertoire';
  end if;

  if target_songs is null or jsonb_typeof(target_songs) <> 'array' then
    raise exception 'The repertoire songs must be an array';
  end if;

  select a.worship_leader_id
  into culto_director
  from public.activities a
  where a.id = target_activity_id
    and a.kind = 'activity'
    and a.category = 'Culto';

  if not found then
    raise exception 'The repertoire must belong to a Culto';
  end if;

  if culto_director is null then
    raise exception 'The Culto needs a worship director';
  end if;

  insert into public.worship_sets (activity_id, ministry_id, director_id, updated_by)
  values (target_activity_id, target_ministry_id, culto_director, (select auth.uid()))
  on conflict (activity_id) do nothing;

  select ws.id, ws.ministry_id
  into saved_set_id, saved_set_ministry_id
  from public.worship_sets ws
  where ws.activity_id = target_activity_id;

  if saved_set_id is null
    or not (select public.can_manage_ministry(saved_set_ministry_id)) then
    raise exception 'The repertoire could not be saved';
  end if;

  delete from public.worship_set_songs where set_id = saved_set_id;

  insert into public.worship_set_songs (
    set_id, ministry_id, song_id, position, song_key, lead_id
  )
  select
    saved_set_id,
    saved_set_ministry_id,
    (entries.song ->> 'song_id')::uuid,
    entries.song_position::smallint,
    coalesce(nullif(trim(entries.song ->> 'song_key'), ''), 'C'),
    nullif(entries.song ->> 'lead_id', '')::uuid
  from jsonb_array_elements(target_songs)
    with ordinality as entries(song, song_position);

  -- Una sola actualización del repertorio genera una sola notificación.
  update public.worship_sets
  set director_id = culto_director,
      updated_by = (select auth.uid())
  where id = saved_set_id;

  return saved_set_id;
end;
$$;

revoke all on function public.save_worship_set(uuid, uuid, jsonb)
  from public, anon;
grant execute on function public.save_worship_set(uuid, uuid, jsonb)
  to authenticated;

create or replace function public.notify_worship_set_update()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  culto_title text;
  culto_date date;
  culto_preacher uuid;
  culto_director uuid;
begin
  select a.title, a.activity_date, a.preacher_id, a.worship_leader_id
  into culto_title, culto_date, culto_preacher, culto_director
  from public.activities a
  where a.id = new.activity_id;

  insert into public.notifications (user_id, message, type)
  select
    recipient.user_id,
    'Se actualizó el repertorio de "' || coalesce(culto_title, 'el culto') || '"' ||
      coalesce(' del ' || to_char(culto_date, 'DD-MM-YYYY'), '') || '.',
    'system'
  from (
    select mm.user_id
    from public.ministry_memberships mm
    where mm.ministry_id = new.ministry_id
    union
    select p.id from public.profiles p where p.role = 'pastor'
    union
    select culto_preacher
    union
    select culto_director
  ) recipient
  join public.profiles active_profile
    on active_profile.id = recipient.user_id
   and active_profile.is_active
  where recipient.user_id is distinct from (select auth.uid());

  return new;
end;
$$;

revoke all on function public.notify_worship_set_update()
  from public, anon, authenticated;

drop trigger if exists worship_sets_notify_update on public.worship_sets;
create trigger worship_sets_notify_update
  after update on public.worship_sets
  for each row execute function public.notify_worship_set_update();

-- Notificaciones de culto y cambios de horario -------------------------------

create or replace function public.notify_worship_roles()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.kind <> 'activity' or new.category <> 'Culto' then
    return new;
  end if;

  if new.preacher_id is not null
    and (tg_op = 'INSERT' or new.preacher_id is distinct from old.preacher_id) then
    insert into public.notifications (user_id, message, type)
    values (
      new.preacher_id,
      'Fuiste asignado como predicador de "' || new.title || '" el ' ||
        to_char(new.activity_date, 'DD-MM-YYYY') || '.',
      'assignment'
    );
  end if;

  if new.worship_leader_id is not null
    and (tg_op = 'INSERT' or new.worship_leader_id is distinct from old.worship_leader_id) then
    insert into public.notifications (user_id, message, type)
    values (
      new.worship_leader_id,
      'Fuiste asignado como director de alabanza de "' || new.title || '" el ' ||
        to_char(new.activity_date, 'DD-MM-YYYY') || '.',
      'assignment'
    );
  end if;

  return new;
end;
$$;

revoke all on function public.notify_worship_roles()
  from public, anon, authenticated;

drop trigger if exists activities_notify_worship_roles on public.activities;
create trigger activities_notify_worship_roles
  after insert or update of preacher_id, worship_leader_id, category
  on public.activities
  for each row execute function public.notify_worship_roles();

create or replace function public.notify_activity_schedule_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (new.activity_date, new.start_time, new.end_time, new.location)
    is not distinct from
    (old.activity_date, old.start_time, old.end_time, old.location) then
    return new;
  end if;

  insert into public.notifications (user_id, message, type)
  select
    recipient.user_id,
    'Cambió la agenda de "' || new.title || '": ' ||
      to_char(new.activity_date, 'DD-MM-YYYY') || ' a las ' ||
      to_char(new.start_time, 'HH24:MI') ||
      coalesce(' en ' || nullif(trim(new.location), ''), '') || '.',
    'schedule'
  from (
    select aa.user_id
    from public.activity_assignments aa
    where aa.activity_id = new.id
    union
    select t.assignee_id from public.tasks t where t.activity_id = new.id
    union
    select new.preacher_id
    union
    select new.worship_leader_id
  ) recipient
  join public.profiles active_profile
    on active_profile.id = recipient.user_id
   and active_profile.is_active
  where recipient.user_id is distinct from (select auth.uid());

  return new;
end;
$$;

revoke all on function public.notify_activity_schedule_change()
  from public, anon, authenticated;

drop trigger if exists activities_notify_schedule_change on public.activities;
create trigger activities_notify_schedule_change
  after update of activity_date, start_time, end_time, location
  on public.activities
  for each row execute function public.notify_activity_schedule_change();

-- Notificaciones de reuniones ------------------------------------------------

create or replace function public.notify_meeting_invitation()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  invited_meeting record;
begin
  if new.user_id is not distinct from (select auth.uid()) then
    return new;
  end if;

  select m.title, m.meeting_date, m.start_time
  into invited_meeting
  from public.meetings m
  where m.id = new.meeting_id;

  insert into public.notifications (user_id, message, type)
  values (
    new.user_id,
    'Fuiste invitado a la reunión "' || coalesce(invited_meeting.title, 'sin título') ||
      '" el ' || coalesce(to_char(invited_meeting.meeting_date, 'DD-MM-YYYY'), 'día por confirmar') ||
      coalesce(' a las ' || to_char(invited_meeting.start_time, 'HH24:MI'), '') || '.',
    'schedule'
  );

  return new;
end;
$$;

revoke all on function public.notify_meeting_invitation()
  from public, anon, authenticated;

drop trigger if exists meeting_participants_notify_invitation on public.meeting_participants;
create trigger meeting_participants_notify_invitation
  after insert on public.meeting_participants
  for each row execute function public.notify_meeting_invitation();

create or replace function public.notify_meeting_schedule_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (new.meeting_date, new.start_time, new.end_time, new.location)
    is not distinct from
    (old.meeting_date, old.start_time, old.end_time, old.location) then
    return new;
  end if;

  insert into public.notifications (user_id, message, type)
  select
    mp.user_id,
    'Cambió la reunión "' || new.title || '": ' ||
      to_char(new.meeting_date, 'DD-MM-YYYY') || ' a las ' ||
      to_char(new.start_time, 'HH24:MI') || '.',
    'schedule'
  from public.meeting_participants mp
  join public.profiles active_profile
    on active_profile.id = mp.user_id
   and active_profile.is_active
  where mp.meeting_id = new.id
    and mp.user_id is distinct from (select auth.uid());

  return new;
end;
$$;

revoke all on function public.notify_meeting_schedule_change()
  from public, anon, authenticated;

drop trigger if exists meetings_notify_schedule_change on public.meetings;
create trigger meetings_notify_schedule_change
  after update of meeting_date, start_time, end_time, location
  on public.meetings
  for each row execute function public.notify_meeting_schedule_change();

-- Notificaciones de proyectos ------------------------------------------------

create or replace function public.notify_project_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- El recálculo automático del avance no es un cambio que deba notificarse.
  if (new.name, new.objective, new.description, new.leader_id,
      new.start_date, new.due_date, new.status)
    is not distinct from
    (old.name, old.objective, old.description, old.leader_id,
      old.start_date, old.due_date, old.status) then
    return new;
  end if;

  insert into public.notifications (user_id, message, type)
  select
    recipient.user_id,
    'Se actualizó el proyecto "' || new.name || '".',
    'system'
  from (
    select new.leader_id as user_id
    union
    select aa.user_id
    from public.activity_assignments aa
    join public.activities a on a.id = aa.activity_id
    where a.project_id = new.id
  ) recipient
  join public.profiles active_profile
    on active_profile.id = recipient.user_id
   and active_profile.is_active
  where recipient.user_id is distinct from (select auth.uid());

  return new;
end;
$$;

revoke all on function public.notify_project_change()
  from public, anon, authenticated;

drop trigger if exists projects_notify_change on public.projects;
create trigger projects_notify_change
  after update on public.projects
  for each row execute function public.notify_project_change();

-- Auditoría ------------------------------------------------------------------

alter table public.audit_logs
  add column if not exists detail text not null default '';

create or replace function public.log_management_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  changed_row jsonb;
  change_detail text;
  module_label text;
begin
  if tg_op = 'DELETE' then
    changed_row := to_jsonb(old);
  else
    changed_row := to_jsonb(new);
  end if;

  change_detail := coalesce(
    changed_row ->> 'title',
    changed_row ->> 'name',
    changed_row ->> 'file_name',
    ''
  );

  insert into public.audit_logs (actor_id, action, table_name, record_id, detail)
  values (
    (select auth.uid()),
    tg_op,
    tg_table_name,
    (changed_row ->> 'id')::uuid,
    change_detail
  );

  if tg_op = 'DELETE' and tg_table_name in ('projects', 'activities', 'meetings') then
    module_label := case tg_table_name
      when 'projects' then 'Proyectos'
      when 'activities' then 'Actividades'
      else 'Reuniones'
    end;

    insert into public.notifications (user_id, message, type)
    select
      p.id,
      'Se eliminó un registro de ' || module_label ||
        coalesce(': "' || nullif(change_detail, '') || '"', '') || '.',
      'system'
    from public.profiles p
    where p.role = 'pastor'
      and p.is_active
      and p.id is distinct from (select auth.uid());
  end if;

  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$$;

revoke all on function public.log_management_change()
  from public, anon, authenticated;

commit;

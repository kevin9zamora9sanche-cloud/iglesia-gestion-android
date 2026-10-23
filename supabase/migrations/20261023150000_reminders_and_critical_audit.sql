-- Requerimientos pendientes de notificaciones y auditoría:
-- 1. Aviso de actividades próximas: cada mañana se avisa de lo que ocurre al día
--    siguiente (actividades, cultos, ensayos, reuniones y tareas que vencen).
-- 2. Auditoría de cambios críticos con aviso al Pastor: cambios de rol, cuentas
--    activadas o desactivadas y cambios de ministerios.

begin;

-- 1. Recordatorios ----------------------------------------------------------------

create extension if not exists pg_cron with schema pg_catalog;

-- Evita repetir un recordatorio si el job se ejecuta más de una vez el mismo día.
create table if not exists public.reminder_log (
  item_id uuid not null,
  user_id uuid not null references public.profiles(id) on delete cascade,
  remind_date date not null,
  created_at timestamptz not null default now(),
  primary key (item_id, user_id, remind_date)
);

alter table public.reminder_log enable row level security;
revoke all on public.reminder_log from public, anon, authenticated;

create or replace function public.send_upcoming_reminders()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  tomorrow date := (now() at time zone 'America/Santiago')::date + 1;
  sent integer := 0;
begin
  with due as (
    -- Actividades, cultos y ensayos: responsables o invitados, predicador y director.
    select a.id as item_id, recipient.user_id,
      'Mañana: "' || a.title || '" a las ' || to_char(a.start_time, 'HH24:MI') ||
        coalesce(' en ' || nullif(trim(a.location), ''), '') || '.' as message
    from public.activities a
    cross join lateral (
      select aa.user_id from public.activity_assignments aa where aa.activity_id = a.id
      union select a.preacher_id
      union select a.worship_leader_id
    ) recipient
    where a.kind = 'activity'
      and a.activity_date = tomorrow
      and a.status::text not in ('completed', 'cancelled')
      and recipient.user_id is not null

    union all

    -- Reuniones: personas invitadas.
    select m.id, mp.user_id,
      'Mañana: reunión "' || m.title || '" a las ' || to_char(m.start_time, 'HH24:MI') ||
        coalesce(' en ' || nullif(trim(m.location), ''), '') || '.'
    from public.meetings m
    join public.meeting_participants mp on mp.meeting_id = m.id
    where m.meeting_date = tomorrow
      and m.status::text not in ('completed', 'cancelled')

    union all

    -- Tareas que vencen mañana y aún no están completadas.
    select t.id, t.assignee_id, 'Tu tarea "' || t.title || '" vence mañana.'
    from public.tasks t
    where (t.due_at at time zone 'America/Santiago')::date = tomorrow
      and t.status::text not in ('completed', 'cancelled')
  ),
  fresh as (
    insert into public.reminder_log (item_id, user_id, remind_date)
    select distinct d.item_id, d.user_id, tomorrow
    from due d
    join public.profiles p on p.id = d.user_id and p.is_active
    on conflict do nothing
    returning item_id, user_id
  )
  insert into public.notifications (user_id, message, type)
  select distinct on (f.item_id, f.user_id) f.user_id, d.message, 'schedule'
  from fresh f
  join due d on d.item_id = f.item_id and d.user_id = f.user_id;

  get diagnostics sent = row_count;

  -- El registro solo se necesita unos días.
  delete from public.reminder_log where remind_date < tomorrow - 7;

  return sent;
end;
$$;

revoke all on function public.send_upcoming_reminders() from public, anon, authenticated;

-- Todos los días a las 11:00 UTC (08:00 en Chile en horario de verano).
select cron.unschedule(jobid) from cron.job where jobname = 'upcoming-reminders';
select cron.schedule('upcoming-reminders', '0 11 * * *', 'select public.send_upcoming_reminders()');

-- 2. Auditoría de cambios críticos ---------------------------------------------

-- Avisa a los Pastores activos, salvo a quien hizo el cambio.
create or replace function public.notify_pastors_critical(change_message text)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.notifications (user_id, message, type)
  select p.id, change_message, 'system'
  from public.profiles p
  where p.role = 'pastor'
    and p.is_active
    and p.id is distinct from (select auth.uid());
$$;

revoke all on function public.notify_pastors_critical(text) from public, anon, authenticated;

create or replace function public.role_label(role_value text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case role_value
    when 'pastor' then 'Pastor/Administrador'
    when 'ministry_leader' then 'Líder de ministerio'
    when 'leader' then 'Líder normal'
    else role_value
  end;
$$;

revoke all on function public.role_label(text) from public, anon;

create or replace function public.audit_profile_access_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  person text := coalesce(nullif(trim(new.full_name), ''), 'una cuenta');
  change_detail text;
begin
  if new.role is distinct from old.role then
    change_detail := 'Rol de ' || person || ': ' || public.role_label(old.role::text) ||
      ' → ' || public.role_label(new.role::text);
  end if;
  if new.is_active is distinct from old.is_active then
    change_detail := coalesce(change_detail || '. ', '') ||
      case when new.is_active then 'Se reactivó la cuenta de ' else 'Se desactivó la cuenta de ' end || person;
  end if;
  if change_detail is null then
    return new;
  end if;

  insert into public.audit_logs (actor_id, action, table_name, record_id, detail)
  values ((select auth.uid()), 'UPDATE', 'profiles', new.id, change_detail);
  perform public.notify_pastors_critical(change_detail || '.');
  return new;
end;
$$;

revoke all on function public.audit_profile_access_change() from public, anon, authenticated;

drop trigger if exists profiles_audit_access_change on public.profiles;
create trigger profiles_audit_access_change
  after update of role, is_active on public.profiles
  for each row execute function public.audit_profile_access_change();

create or replace function public.audit_ministry_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  change_detail text;
begin
  if tg_op = 'INSERT' then
    change_detail := 'Se creó el ministerio ' || new.name;
  elsif tg_op = 'DELETE' then
    change_detail := 'Se eliminó el ministerio ' || old.name;
  elsif new.is_active is distinct from old.is_active then
    change_detail := case when new.is_active then 'Se reactivó el ministerio ' else 'Se desactivó el ministerio ' end || new.name;
  elsif new.name is distinct from old.name then
    change_detail := 'El ministerio ' || old.name || ' ahora se llama ' || new.name;
  else
    return new;
  end if;

  insert into public.audit_logs (actor_id, action, table_name, record_id, detail)
  values ((select auth.uid()), tg_op, 'ministries', coalesce(new.id, old.id), change_detail);
  perform public.notify_pastors_critical(change_detail || '.');
  return coalesce(new, old);
end;
$$;

revoke all on function public.audit_ministry_change() from public, anon, authenticated;

drop trigger if exists ministries_audit_change on public.ministries;
create trigger ministries_audit_change
  after insert or update or delete on public.ministries
  for each row execute function public.audit_ministry_change();

commit;

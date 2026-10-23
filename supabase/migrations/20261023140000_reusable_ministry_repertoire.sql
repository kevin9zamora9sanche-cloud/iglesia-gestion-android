-- Repertorio único y reutilizable del ministerio de Alabanza.
-- Antes cada Culto tenía su propio repertorio; ahora el ministerio mantiene uno
-- solo (worship_sets con activity_id nulo) que se usa en todos los cultos y
-- ensayos. Los repertorios por Culto existentes se conservan por compatibilidad.
-- Cada canción guarda orden, tonalidad y director opcional; la versión y los
-- archivos vienen del catálogo (worship_songs, worship_song_files).

begin;

alter table public.worship_sets alter column activity_id drop not null;
alter table public.worship_sets alter column director_id drop not null;

create unique index if not exists worship_sets_one_ministry_repertoire_idx
  on public.worship_sets(ministry_id)
  where activity_id is null;

create or replace function public.save_ministry_repertoire(
  target_ministry_id uuid,
  target_songs jsonb
)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  saved_set_id uuid;
begin
  if not (select public.can_manage_ministry(target_ministry_id)) then
    raise exception 'Only worship ministry leaders may edit the repertoire';
  end if;

  if target_songs is null or jsonb_typeof(target_songs) <> 'array' then
    raise exception 'The repertoire songs must be an array';
  end if;

  insert into public.worship_sets (activity_id, ministry_id, director_id, updated_by)
  values (null, target_ministry_id, null, (select auth.uid()))
  on conflict (ministry_id) where activity_id is null do nothing;

  select ws.id
  into saved_set_id
  from public.worship_sets ws
  where ws.ministry_id = target_ministry_id
    and ws.activity_id is null;

  if saved_set_id is null then
    raise exception 'The repertoire could not be saved';
  end if;

  delete from public.worship_set_songs where set_id = saved_set_id;

  insert into public.worship_set_songs (
    set_id, ministry_id, song_id, position, song_key, lead_id
  )
  select
    saved_set_id,
    target_ministry_id,
    (entries.song ->> 'song_id')::uuid,
    entries.song_position::smallint,
    coalesce(nullif(trim(entries.song ->> 'song_key'), ''), 'C'),
    nullif(entries.song ->> 'lead_id', '')::uuid
  from jsonb_array_elements(target_songs)
    with ordinality as entries(song, song_position);

  -- Una sola actualización del repertorio genera una sola notificación.
  update public.worship_sets
  set updated_by = (select auth.uid())
  where id = saved_set_id;

  return saved_set_id;
end;
$$;

revoke all on function public.save_ministry_repertoire(uuid, jsonb)
  from public, anon;
grant execute on function public.save_ministry_repertoire(uuid, jsonb)
  to authenticated;

-- El aviso del repertorio del ministerio llega a Alabanza, al Pastor y a los
-- predicadores y directores de los cultos que aún no ocurren.
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
  if new.activity_id is null then
    insert into public.notifications (user_id, message, type)
    select recipient.user_id, 'Se actualizó el repertorio de Alabanza.', 'system'
    from (
      select mm.user_id
      from public.ministry_memberships mm
      where mm.ministry_id = new.ministry_id
      union
      select p.id from public.profiles p where p.role = 'pastor'
      union
      select a.preacher_id
      from public.activities a
      where a.kind = 'activity' and a.category = 'Culto' and a.activity_date >= current_date
      union
      select a.worship_leader_id
      from public.activities a
      where a.kind = 'activity' and a.category = 'Culto' and a.activity_date >= current_date
    ) recipient
    join public.profiles active_profile
      on active_profile.id = recipient.user_id
     and active_profile.is_active
    where recipient.user_id is distinct from (select auth.uid());

    return new;
  end if;

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

commit;

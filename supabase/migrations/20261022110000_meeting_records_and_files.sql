-- Registros posteriores a reuniones, separados de las convocatorias (meetings).
-- El Pastor registra nombre, fecha, resumen, archivos y visibilidad: toda la
-- iglesia, un ministerio o personas seleccionadas. Solo quien tiene permiso
-- sobre el registro puede ver y descargar sus archivos (PDF, JPG o PNG, 5 MB).

begin;

create table if not exists public.meeting_records (
  id uuid primary key default extensions.gen_random_uuid(),
  meeting_id uuid references public.meetings(id) on delete set null,
  title text not null check (length(trim(title)) > 0),
  record_date date not null,
  summary text not null default '',
  visibility text not null check (visibility in ('everyone', 'ministry', 'selected')),
  ministry_id uuid references public.ministries(id) on delete restrict,
  created_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint meeting_records_ministry_visibility check (
    (visibility = 'ministry') = (ministry_id is not null)
  )
);

create index if not exists meeting_records_date_idx
  on public.meeting_records(record_date desc);

create table if not exists public.meeting_record_viewers (
  record_id uuid not null references public.meeting_records(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (record_id, user_id)
);

create index if not exists meeting_record_viewers_user_idx
  on public.meeting_record_viewers(user_id, record_id);

create table if not exists public.meeting_record_files (
  id uuid primary key default extensions.gen_random_uuid(),
  record_id uuid not null references public.meeting_records(id) on delete cascade,
  file_name text not null check (length(trim(file_name)) > 0),
  content_type text not null check (
    content_type in ('application/pdf', 'image/jpeg', 'image/png')
  ),
  size_bytes bigint not null check (size_bytes between 1 and 5242880),
  object_path text not null unique,
  uploaded_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  constraint meeting_record_files_path check (
    object_path like record_id::text || '/%'
  )
);

create index if not exists meeting_record_files_record_idx
  on public.meeting_record_files(record_id);

alter table public.meeting_records enable row level security;
alter table public.meeting_record_viewers enable row level security;
alter table public.meeting_record_files enable row level security;

create or replace function public.can_view_meeting_record(target_record_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select public.is_active_user())
    and exists (
      select 1
      from public.meeting_records r
      where r.id = target_record_id
        and (
          (select public.is_pastor())
          or r.created_by = (select auth.uid())
          or r.visibility = 'everyone'
          or (r.visibility = 'ministry' and (select public.is_ministry_member(r.ministry_id)))
          or (
            r.visibility = 'selected'
            and exists (
              select 1
              from public.meeting_record_viewers v
              where v.record_id = r.id
                and v.user_id = (select auth.uid())
            )
          )
        )
    );
$$;

revoke all on function public.can_view_meeting_record(uuid) from public, anon;
grant execute on function public.can_view_meeting_record(uuid) to authenticated;

revoke all on public.meeting_records, public.meeting_record_viewers,
  public.meeting_record_files from anon, authenticated;
grant select, insert, delete on public.meeting_records,
  public.meeting_record_viewers, public.meeting_record_files to authenticated;
grant update (meeting_id, title, record_date, summary, visibility, ministry_id)
  on public.meeting_records to authenticated;

drop policy if exists "Allowed users can view meeting records" on public.meeting_records;
create policy "Allowed users can view meeting records"
  on public.meeting_records for select to authenticated
  using ((select public.can_view_meeting_record(id)));

drop policy if exists "Pastors can create meeting records" on public.meeting_records;
create policy "Pastors can create meeting records"
  on public.meeting_records for insert to authenticated
  with check (
    (select public.is_active_user())
    and (select public.is_pastor())
    and created_by = (select auth.uid())
  );

drop policy if exists "Pastors can update meeting records" on public.meeting_records;
create policy "Pastors can update meeting records"
  on public.meeting_records for update to authenticated
  using ((select public.is_pastor()))
  with check ((select public.is_pastor()));

drop policy if exists "Pastors can delete meeting records" on public.meeting_records;
create policy "Pastors can delete meeting records"
  on public.meeting_records for delete to authenticated
  using ((select public.is_pastor()));

drop policy if exists "Pastors and selected users can view record viewers" on public.meeting_record_viewers;
create policy "Pastors and selected users can view record viewers"
  on public.meeting_record_viewers for select to authenticated
  using (
    (select public.is_active_user())
    and ((select public.is_pastor()) or user_id = (select auth.uid()))
  );

drop policy if exists "Pastors can select record viewers" on public.meeting_record_viewers;
create policy "Pastors can select record viewers"
  on public.meeting_record_viewers for insert to authenticated
  with check ((select public.is_pastor()));

drop policy if exists "Pastors can remove record viewers" on public.meeting_record_viewers;
create policy "Pastors can remove record viewers"
  on public.meeting_record_viewers for delete to authenticated
  using ((select public.is_pastor()));

drop policy if exists "Allowed users can view record files" on public.meeting_record_files;
create policy "Allowed users can view record files"
  on public.meeting_record_files for select to authenticated
  using ((select public.can_view_meeting_record(record_id)));

drop policy if exists "Pastors can add record files" on public.meeting_record_files;
create policy "Pastors can add record files"
  on public.meeting_record_files for insert to authenticated
  with check (
    (select public.is_pastor())
    and uploaded_by = (select auth.uid())
  );

drop policy if exists "Pastors can delete record files" on public.meeting_record_files;
create policy "Pastors can delete record files"
  on public.meeting_record_files for delete to authenticated
  using ((select public.is_pastor()));

drop trigger if exists meeting_records_set_updated_at on public.meeting_records;
create trigger meeting_records_set_updated_at
  before update on public.meeting_records
  for each row execute function public.set_updated_at();

drop trigger if exists meeting_records_audit on public.meeting_records;
create trigger meeting_records_audit
  after insert or update or delete on public.meeting_records
  for each row execute function public.log_management_change();

drop trigger if exists meeting_record_files_audit on public.meeting_record_files;
create trigger meeting_record_files_audit
  after insert or delete on public.meeting_record_files
  for each row execute function public.log_management_change();

-- Notificaciones: toda la iglesia o el ministerio al crear el registro; las
-- personas seleccionadas al ser agregadas como lectoras.
create or replace function public.notify_meeting_record()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.visibility = 'selected' then
    return new;
  end if;

  insert into public.notifications (user_id, message, type)
  select
    p.id,
    'Nuevo registro de reunión: "' || new.title || '" (' ||
      to_char(new.record_date, 'DD-MM-YYYY') || ').',
    'system'
  from public.profiles p
  where p.is_active
    and p.id is distinct from (select auth.uid())
    and (
      new.visibility = 'everyone'
      or exists (
        select 1
        from public.ministry_memberships mm
        where mm.ministry_id = new.ministry_id
          and mm.user_id = p.id
      )
    );

  return new;
end;
$$;

revoke all on function public.notify_meeting_record() from public, anon, authenticated;

drop trigger if exists meeting_records_notify on public.meeting_records;
create trigger meeting_records_notify
  after insert on public.meeting_records
  for each row execute function public.notify_meeting_record();

create or replace function public.notify_meeting_record_viewer()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  record_title text;
begin
  if new.user_id is not distinct from (select auth.uid()) then
    return new;
  end if;

  select r.title into record_title
  from public.meeting_records r
  where r.id = new.record_id;

  insert into public.notifications (user_id, message, type)
  values (
    new.user_id,
    'Se compartió contigo el registro de reunión "' ||
      coalesce(record_title, 'sin título') || '".',
    'system'
  );

  return new;
end;
$$;

revoke all on function public.notify_meeting_record_viewer() from public, anon, authenticated;

drop trigger if exists meeting_record_viewers_notify on public.meeting_record_viewers;
create trigger meeting_record_viewers_notify
  after insert on public.meeting_record_viewers
  for each row execute function public.notify_meeting_record_viewer();

-- Bucket privado propio: las rutas son <record_id>/<archivo>, por lo que la
-- descarga hereda los permisos del registro.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'meeting-records',
  'meeting-records',
  false,
  5242880,
  array['application/pdf', 'image/jpeg', 'image/png']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Allowed users can read meeting record files" on storage.objects;
create policy "Allowed users can read meeting record files"
  on storage.objects for select to authenticated
  using (
    bucket_id = 'meeting-records'
    and (storage.foldername(name))[1] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and (select public.can_view_meeting_record(((storage.foldername(name))[1])::uuid))
  );

drop policy if exists "Pastors can upload meeting record files" on storage.objects;
create policy "Pastors can upload meeting record files"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'meeting-records'
    and (select public.is_active_user())
    and (select public.is_pastor())
  );

drop policy if exists "Pastors can delete meeting record files" on storage.objects;
create policy "Pastors can delete meeting record files"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'meeting-records'
    and (select public.is_pastor())
  );

commit;

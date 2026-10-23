-- Archivos de canciones del catálogo de Alabanza (partituras, letras, acordes).
-- Solo PDF, JPG o PNG de hasta 5 MB. Los ve cualquier usuario activo, igual que
-- el catálogo; los suben y eliminan el Pastor y los líderes de Alabanza.
-- El límite de archivos por canción sigue pendiente de validación con el cliente.

begin;

create table if not exists public.worship_song_files (
  id uuid primary key default extensions.gen_random_uuid(),
  song_id uuid not null references public.worship_songs(id) on delete cascade,
  file_name text not null check (length(trim(file_name)) > 0),
  content_type text not null check (
    content_type in ('application/pdf', 'image/jpeg', 'image/png')
  ),
  size_bytes bigint not null check (size_bytes between 1 and 5242880),
  object_path text not null unique,
  uploaded_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  constraint worship_song_files_path check (
    object_path like song_id::text || '/%'
  )
);

create index if not exists worship_song_files_song_idx
  on public.worship_song_files(song_id);

alter table public.worship_song_files enable row level security;

create or replace function public.can_manage_song(target_song_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.worship_songs s
    where s.id = target_song_id
      and public.can_manage_ministry(s.ministry_id)
  );
$$;

revoke all on function public.can_manage_song(uuid) from public, anon;
grant execute on function public.can_manage_song(uuid) to authenticated;

revoke all on public.worship_song_files from anon, authenticated;
grant select, insert, delete on public.worship_song_files to authenticated;

drop policy if exists "Active users can view song files" on public.worship_song_files;
create policy "Active users can view song files"
  on public.worship_song_files for select to authenticated
  using ((select public.is_active_user()));

drop policy if exists "Worship leaders can add song files" on public.worship_song_files;
create policy "Worship leaders can add song files"
  on public.worship_song_files for insert to authenticated
  with check (
    (select public.is_active_user())
    and uploaded_by = (select auth.uid())
    and (select public.can_manage_song(song_id))
  );

drop policy if exists "Worship leaders can delete song files" on public.worship_song_files;
create policy "Worship leaders can delete song files"
  on public.worship_song_files for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_song(song_id))
  );

drop trigger if exists worship_song_files_audit on public.worship_song_files;
create trigger worship_song_files_audit
  after insert or delete on public.worship_song_files
  for each row execute function public.log_management_change();

-- Bucket privado propio: las rutas son <song_id>/<archivo>, por lo que los
-- permisos de Storage se derivan de la canción.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'worship-songs',
  'worship-songs',
  false,
  5242880,
  array['application/pdf', 'image/jpeg', 'image/png']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Active users can read worship song files" on storage.objects;
create policy "Active users can read worship song files"
  on storage.objects for select to authenticated
  using (
    bucket_id = 'worship-songs'
    and (select public.is_active_user())
  );

drop policy if exists "Worship leaders can upload worship song files" on storage.objects;
create policy "Worship leaders can upload worship song files"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'worship-songs'
    and (select public.is_active_user())
    and (storage.foldername(name))[1] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and (select public.can_manage_song(((storage.foldername(name))[1])::uuid))
  );

drop policy if exists "Worship leaders can delete worship song files" on storage.objects;
create policy "Worship leaders can delete worship song files"
  on storage.objects for delete to authenticated
  using (
    bucket_id = 'worship-songs'
    and (storage.foldername(name))[1] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and (select public.can_manage_song(((storage.foldername(name))[1])::uuid))
  );

commit;

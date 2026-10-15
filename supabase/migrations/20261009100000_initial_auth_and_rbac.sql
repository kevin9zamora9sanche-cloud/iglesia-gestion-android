create extension if not exists pgcrypto with schema extensions;

do $$ 
begin
  if not exists (select 1 from pg_type where typname = 'app_role') then
    create type public.app_role as enum (
      'pastor',
      'ministry_leader',
      'leader'
    );
  end if;
end $$;

CREATE TABLE IF NOT EXISTS public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null default '',
  phone text,
  role public.app_role not null default 'leader',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

CREATE TABLE IF NOT EXISTS public.ministries (
  id uuid primary key default extensions.gen_random_uuid(),
  name text not null unique,
  description text not null default '',
  is_active boolean not null default true,
  created_by uuid not null references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

CREATE TABLE IF NOT EXISTS public.ministry_memberships (
  ministry_id uuid not null references public.ministries(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  is_leader boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (ministry_id, user_id)
);

CREATE INDEX IF NOT EXISTS ministry_memberships_user_id_idx
  on public.ministry_memberships(user_id);

CREATE OR REPLACE FUNCTION public.is_pastor()
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
      and p.role = 'pastor'
      and p.is_active
  );
$$;

CREATE OR REPLACE FUNCTION public.can_manage_ministry(target_ministry_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select public.is_pastor()
    or exists (
      select 1
      from public.ministry_memberships mm
      join public.profiles p on p.id = mm.user_id
      where mm.ministry_id = target_ministry_id
        and mm.user_id = (select auth.uid())
        and mm.is_leader
        and p.role = 'ministry_leader'
        and p.is_active
    );
$$;

CREATE OR REPLACE FUNCTION public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, full_name)
  values (
    new.id,
    coalesce(nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''), '')
  );
  return new;
end;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

CREATE OR REPLACE FUNCTION public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

DROP TRIGGER IF EXISTS profiles_set_updated_at ON public.profiles;
create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

DROP TRIGGER IF EXISTS ministries_set_updated_at ON public.ministries;
create trigger ministries_set_updated_at
  before update on public.ministries
  for each row execute function public.set_updated_at();

alter table public.profiles enable row level security;
alter table public.ministries enable row level security;
alter table public.ministry_memberships enable row level security;

revoke all on public.profiles, public.ministries, public.ministry_memberships
  from anon, authenticated;
revoke all on function public.is_pastor() from public, anon;
revoke all on function public.can_manage_ministry(uuid) from public, anon;
grant select on public.profiles to authenticated;
grant update (full_name, phone) on public.profiles to authenticated;
grant select, insert, update, delete on public.ministries to authenticated;
grant select, insert, update, delete on public.ministry_memberships to authenticated;
grant execute on function public.is_pastor() to authenticated;
grant execute on function public.can_manage_ministry(uuid) to authenticated;

DROP POLICY IF EXISTS "Users can read their own profile; pastors can read all" ON public.profiles;
create policy "Users can read their own profile; pastors can read all"
  on public.profiles for select to authenticated
  using (id = (select auth.uid()) or (select public.is_pastor()));

DROP POLICY IF EXISTS "Users can update their own contact details" ON public.profiles;
create policy "Users can update their own contact details"
  on public.profiles for update to authenticated
  using (
    (id = (select auth.uid()) and is_active)
    or (select public.is_pastor())
  )
  with check (
    (id = (select auth.uid()) and is_active)
    or (select public.is_pastor())
  );

DROP POLICY IF EXISTS "Authenticated users can view active ministries" ON public.ministries;
create policy "Authenticated users can view active ministries"
  on public.ministries for select to authenticated
  using (is_active or (select public.is_pastor()));

DROP POLICY IF EXISTS "Pastors can create ministries" ON public.ministries;
create policy "Pastors can create ministries"
  on public.ministries for insert to authenticated
  with check ((select public.is_pastor()) and created_by = (select auth.uid()));

DROP POLICY IF EXISTS "Pastors can update ministries" ON public.ministries;
create policy "Pastors can update ministries"
  on public.ministries for update to authenticated
  using ((select public.is_pastor()))
  with check ((select public.is_pastor()));

DROP POLICY IF EXISTS "Pastors can delete ministries" ON public.ministries;
create policy "Pastors can delete ministries"
  on public.ministries for delete to authenticated
  using ((select public.is_pastor()));

DROP POLICY IF EXISTS "Users can view their ministry memberships; pastors can view all" ON public.ministry_memberships;
create policy "Users can view their ministry memberships; pastors can view all"
  on public.ministry_memberships for select to authenticated
  using (
    user_id = (select auth.uid())
    or (select public.is_pastor())
    or (select public.can_manage_ministry(ministry_id))
  );

DROP POLICY IF EXISTS "Pastors can add ministry memberships" ON public.ministry_memberships;
create policy "Pastors can add ministry memberships"
  on public.ministry_memberships for insert to authenticated
  with check ((select public.is_pastor()));

DROP POLICY IF EXISTS "Pastors can update ministry memberships" ON public.ministry_memberships;
create policy "Pastors can update ministry memberships"
  on public.ministry_memberships for update to authenticated
  using ((select public.is_pastor()))
  with check ((select public.is_pastor()));

DROP POLICY IF EXISTS "Pastors can remove ministry memberships" ON public.ministry_memberships;
create policy "Pastors can remove ministry memberships"
  on public.ministry_memberships for delete to authenticated
  using ((select public.is_pastor()));

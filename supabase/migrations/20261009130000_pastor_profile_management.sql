begin;

CREATE OR REPLACE FUNCTION public.admin_update_profile(
  target_profile_id uuid,
  target_full_name text,
  target_phone text,
  target_role public.app_role,
  target_is_active boolean
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not public.is_pastor() then
    raise exception 'Only active pastors may manage profiles';
  end if;

  if target_full_name is null or length(trim(target_full_name)) = 0 then
    raise exception 'Profile name cannot be empty';
  end if;

  if not exists (
    select 1 from public.profiles where id = target_profile_id
  ) then
    raise exception 'Profile not found';
  end if;

  if target_role <> 'pastor' or not target_is_active then
    perform pg_catalog.pg_advisory_xact_lock(741031902);
    if not exists (
      select 1
      from public.profiles p
      where p.id <> target_profile_id
        and p.role = 'pastor'
        and p.is_active
    ) then
      raise exception 'At least one active pastor must remain';
    end if;
  end if;

  update public.profiles
  set full_name = trim(target_full_name),
      phone = nullif(trim(target_phone), ''),
      role = target_role,
      is_active = target_is_active
  where id = target_profile_id;
end;
$$;

revoke all on function public.admin_update_profile(
  uuid, text, text, public.app_role, boolean
) from public, anon;
grant execute on function public.admin_update_profile(
  uuid, text, text, public.app_role, boolean
) to authenticated;

commit;

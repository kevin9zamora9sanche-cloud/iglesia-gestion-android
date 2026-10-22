-- Directorio de cuentas para el Pastor/Administrador: correo y último acceso.
-- profiles no guarda el correo; esta función lo expone solo a Pastores activos.

begin;

create or replace function public.admin_list_users()
returns table (
  id uuid,
  email text,
  last_sign_in_at timestamptz,
  created_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
  if not public.is_pastor() then
    raise exception 'Only active pastors may list user accounts';
  end if;

  return query
  select u.id, u.email::text, u.last_sign_in_at, u.created_at
  from auth.users u
  order by u.created_at;
end;
$$;

revoke all on function public.admin_list_users() from public, anon;
grant execute on function public.admin_list_users() to authenticated;

commit;

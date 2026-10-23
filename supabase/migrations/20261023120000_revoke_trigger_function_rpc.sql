-- Las funciones de trigger no deben quedar expuestas como RPC (/rest/v1/rpc/...).
-- Postgres verifica EXECUTE solo al crear el trigger, no al dispararse, por lo
-- que los triggers existentes siguen funcionando.
-- rls_auto_enable es el event trigger de Supabase que activa RLS en tablas nuevas.

begin;

revoke all on function public.handle_new_user() from public, anon, authenticated;

do $$
begin
  if exists (
    select 1
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.proname = 'rls_auto_enable'
  ) then
    revoke all on function public.rls_auto_enable() from public, anon, authenticated;
  end if;
end;
$$;

commit;

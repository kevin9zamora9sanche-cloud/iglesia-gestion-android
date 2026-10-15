begin;

alter table public.projects
  add column if not exists objective text not null default '',
  add column if not exists budget_executed numeric(14, 2);

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'projects_objective_nonempty'
      and conrelid = 'public.projects'::regclass
  ) then
    alter table public.projects
      add constraint projects_objective_nonempty
      check (length(trim(objective)) > 0) not valid;
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conname = 'projects_budget_executed_nonnegative'
      and conrelid = 'public.projects'::regclass
  ) then
    alter table public.projects
      add constraint projects_budget_executed_nonnegative
      check (budget_executed is null or budget_executed >= 0);
  end if;
end $$;

revoke update on public.projects from authenticated;
grant update (
  name, objective, description, leader_id, start_date, due_date,
  budget, budget_executed, status
) on public.projects to authenticated;

alter type public.project_status add value if not exists 'paused';

drop policy if exists "Pastors and ministry leaders can create projects"
  on public.projects;
DROP POLICY IF EXISTS "Pastors can create institutional projects" ON public.projects;
create policy "Pastors can create institutional projects"
  on public.projects for insert to authenticated
  with check (
    (select public.is_active_user())
    and (select public.is_pastor())
    and created_by = (select auth.uid())
    and ministry_id is null
  );

drop policy if exists "Pastors and ministry leaders can update projects"
  on public.projects;
DROP POLICY IF EXISTS "Pastors can update institutional projects" ON public.projects;
create policy "Pastors can update institutional projects"
  on public.projects for update to authenticated
  using (
    (select public.is_active_user())
    and (select public.is_pastor())
  )
  with check (
    (select public.is_active_user())
    and (select public.is_pastor())
    and ministry_id is null
  );

drop policy if exists "Pastors and ministry leaders can delete projects"
  on public.projects;
DROP POLICY IF EXISTS "Pastors can delete projects" ON public.projects;
create policy "Pastors can delete projects"
  on public.projects for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.is_pastor())
  );

commit;

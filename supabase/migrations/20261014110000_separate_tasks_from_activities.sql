begin;

alter type public.task_status add value if not exists 'paused';

CREATE TABLE IF NOT EXISTS public.tasks (
  id uuid primary key default extensions.gen_random_uuid(),
  activity_id uuid not null references public.activities(id) on delete cascade,
  title text not null check (length(trim(title)) > 0),
  instructions text not null check (length(trim(instructions)) > 0),
  assignee_id uuid not null references public.profiles(id) on delete restrict,
  due_at timestamptz,
  priority public.task_priority not null default 'medium',
  status public.task_status not null default 'pending',
  comments text not null default '',
  assigned_by uuid not null default auth.uid() references public.profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint tasks_status_not_cancelled check (status <> 'cancelled')
);

CREATE INDEX IF NOT EXISTS tasks_activity_id_idx on public.tasks(activity_id);
CREATE INDEX IF NOT EXISTS tasks_assignee_due_at_idx on public.tasks(assignee_id, due_at);

alter table public.tasks enable row level security;

CREATE OR REPLACE FUNCTION public.can_assign_task_to_user(
  target_user_id uuid,
  target_activity_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.profiles p
    join public.activities a on a.id = target_activity_id
    where p.id = target_user_id
      and p.is_active
      and (
        (select public.is_pastor())
        or (
          a.ministry_id is not null
          and (select public.can_manage_ministry(a.ministry_id))
          and exists (
            select 1
            from public.ministry_memberships mm
            where mm.ministry_id = a.ministry_id
              and mm.user_id = target_user_id
          )
        )
      )
  );
$$;

revoke all on function public.can_assign_task_to_user(uuid, uuid)
  from public, anon;
grant execute on function public.can_assign_task_to_user(uuid, uuid)
  to authenticated;

CREATE OR REPLACE FUNCTION public.validate_task_parent()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if not exists (
    select 1
    from public.activities a
    where a.id = new.activity_id
      and a.kind = 'activity'
  ) then
    raise exception 'A task must belong to an activity, not another task or meeting';
  end if;

  return new;
end;
$$;

revoke all on function public.validate_task_parent()
  from public, anon, authenticated;

revoke all on public.tasks from anon, authenticated;
grant select, insert, delete on public.tasks to authenticated;
grant update (
  activity_id, title, instructions, assignee_id, due_at,
  priority, status, comments, assigned_by
) on public.tasks to authenticated;

DROP POLICY IF EXISTS "Users can view assigned tasks and managers can view ministry tasks" ON public.tasks;
create policy "Users can view assigned tasks and managers can view ministry tasks"
  on public.tasks for select to authenticated
  using (
    (select public.is_active_user())
    and (
      assignee_id = (select auth.uid())
      or (select public.can_manage_activity(activity_id))
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can create tasks" ON public.tasks;
create policy "Pastors and ministry leaders can create tasks"
  on public.tasks for insert to authenticated
  with check (
    (select public.is_active_user())
    and assigned_by = (select auth.uid())
    and (select public.can_manage_activity(activity_id))
    and (select public.can_assign_task_to_user(assignee_id, activity_id))
  );

DROP POLICY IF EXISTS "Assignees and managers can update tasks" ON public.tasks;
create policy "Assignees and managers can update tasks"
  on public.tasks for update to authenticated
  using (
    (select public.is_active_user())
    and (
      assignee_id = (select auth.uid())
      or (select public.can_manage_activity(activity_id))
    )
  )
  with check (
    (select public.is_active_user())
    and (
      assignee_id = (select auth.uid())
      or (
        (select public.can_manage_activity(activity_id))
        and (select public.can_assign_task_to_user(assignee_id, activity_id))
      )
    )
  );

DROP POLICY IF EXISTS "Pastors and ministry leaders can delete tasks" ON public.tasks;
create policy "Pastors and ministry leaders can delete tasks"
  on public.tasks for delete to authenticated
  using (
    (select public.is_active_user())
    and (select public.can_manage_activity(activity_id))
  );

CREATE OR REPLACE FUNCTION public.restrict_assignee_task_row_updates()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if (select public.is_pastor())
    or (select public.can_manage_activity(old.activity_id)) then
    return new;
  end if;

  if old.assignee_id <> (select auth.uid()) then
    raise exception 'Only the task assignee may update its status';
  end if;

  if (to_jsonb(new) - 'status' - 'updated_at')
    is distinct from
    (to_jsonb(old) - 'status' - 'updated_at') then
    raise exception 'Assignees may only update task status';
  end if;

  return new;
end;
$$;

revoke all on function public.restrict_assignee_task_row_updates()
  from public, anon, authenticated;

DROP TRIGGER IF EXISTS tasks_restrict_assignee_updates ON public.tasks;
create trigger tasks_restrict_assignee_updates
  before update on public.tasks
  for each row execute function public.restrict_assignee_task_row_updates();

DROP TRIGGER IF EXISTS tasks_validate_parent_insert ON public.tasks;
create trigger tasks_validate_parent_insert
  before insert on public.tasks
  for each row execute function public.validate_task_parent();

DROP TRIGGER IF EXISTS tasks_validate_parent_update ON public.tasks;
create trigger tasks_validate_parent_update
  before update of activity_id on public.tasks
  for each row execute function public.validate_task_parent();

CREATE OR REPLACE FUNCTION public.notify_task_assignment()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  parent_title text;
begin
  if current_setting('app.migrating_legacy_tasks', true) = 'true' then
    return new;
  end if;

  if tg_op = 'UPDATE' then
    if old.assignee_id = new.assignee_id then
      return new;
    end if;
  end if;

  select a.title
  into parent_title
  from public.activities a
  where a.id = new.activity_id;

  insert into public.notifications (user_id, message, type)
  values (
    new.assignee_id,
    'Se te asignó la tarea "' || new.title || '" en "' ||
      coalesce(parent_title, 'una actividad') || '".',
    'assignment'
  );

  return new;
end;
$$;

revoke all on function public.notify_task_assignment()
  from public, anon, authenticated;

DROP TRIGGER IF EXISTS task_assignment_notification_insert ON public.tasks;
create trigger task_assignment_notification_insert
  after insert on public.tasks
  for each row execute function public.notify_task_assignment();

DROP TRIGGER IF EXISTS task_assignment_notification_reassignment ON public.tasks;
create trigger task_assignment_notification_reassignment
  after update of assignee_id on public.tasks
  for each row execute function public.notify_task_assignment();

DROP TRIGGER IF EXISTS tasks_set_updated_at ON public.tasks;
create trigger tasks_set_updated_at
  before update on public.tasks
  for each row execute function public.set_updated_at();

DROP TRIGGER IF EXISTS tasks_audit ON public.tasks;
create trigger tasks_audit
  after insert or update or delete on public.tasks
  for each row execute function public.log_management_change();

DROP POLICY IF EXISTS "Active users can view relevant activities" ON public.activities;
create policy "Active users can view relevant activities"
  on public.activities for select to authenticated
  using (
    (select public.is_active_user())
    and (
      (select public.is_pastor())
      or (select public.is_activity_assignee(id))
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

commit;

begin;

CREATE OR REPLACE FUNCTION public.restrict_assignee_task_updates()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if (select public.is_pastor())
    or (select public.can_manage_activity(old.id)) then
    return new;
  end if;

  if old.kind <> 'task'
    or new.kind <> 'task'
    or not (select public.is_activity_assignee(old.id)) then
    raise exception 'Only assigned users may update task status';
  end if;

  if (to_jsonb(new) - 'task_status' - 'updated_at')
    is distinct from
    (to_jsonb(old) - 'task_status' - 'updated_at') then
    raise exception 'Assigned users may only update task_status';
  end if;

  return new;
end;
$$;

revoke all on function public.restrict_assignee_task_updates()
  from public, anon, authenticated;

DROP TRIGGER IF EXISTS activities_restrict_assignee_task_updates ON public.activities;
create trigger activities_restrict_assignee_task_updates
  before update on public.activities
  for each row execute function public.restrict_assignee_task_updates();

commit;

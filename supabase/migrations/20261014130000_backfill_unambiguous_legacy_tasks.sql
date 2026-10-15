begin;

select set_config('app.migrating_legacy_tasks', 'true', true);

with legacy_assignments as (
  select
    activity_id,
    array_agg(user_id) as assignee_ids,
    count(*) as assignment_count
  from public.activity_assignments
  group by activity_id
),
parent_candidates as (
  select
    legacy.id as legacy_id,
    parent.id as parent_id,
    count(*) over (partition by legacy.id) as parent_count
  from public.activities legacy
  join public.activities parent
    on parent.kind = 'activity'
   and parent.id <> legacy.id
   and (
     (
       legacy.project_id is not null
       and parent.project_id = legacy.project_id
     )
     or (
       legacy.project_id is null
       and legacy.ministry_id is not null
       and parent.ministry_id = legacy.ministry_id
     )
   )
  where legacy.kind = 'task'
),
eligible_legacy_tasks as (
  select
    legacy.id,
    parents.parent_id,
    legacy.title,
    legacy.description,
    assignments.assignee_ids[1] as assignee_id,
    legacy.activity_date + legacy.start_time as due_at,
    legacy.priority,
    legacy.task_status as status,
    legacy.created_by as assigned_by
  from public.activities legacy
  join parent_candidates parents
    on parents.legacy_id = legacy.id
   and parents.parent_count = 1
  join legacy_assignments assignments
    on assignments.activity_id = legacy.id
   and assignments.assignment_count = 1
  join public.profiles assignee
    on assignee.id = assignments.assignee_ids[1]
   and assignee.is_active
  where legacy.kind = 'task'
    and legacy.task_status <> 'cancelled'
    and (
      parents.parent_id is not null
    )
    and (
      not exists (
        select 1
        from public.activities parent
        where parent.id = parents.parent_id
          and parent.ministry_id is not null
      )
      or exists (
        select 1
        from public.activities parent
        join public.ministry_memberships membership
          on membership.ministry_id = parent.ministry_id
        where parent.id = parents.parent_id
          and membership.user_id = assignee.id
      )
    )
),
migrated_tasks as (
  insert into public.tasks (
    id, activity_id, title, instructions, assignee_id, due_at,
    priority, status, assigned_by
  )
  select
    id, parent_id, title, description, assignee_id, due_at,
    priority, status, assigned_by
  from eligible_legacy_tasks
  on conflict (id) do update
    set updated_at = now()
  returning id
)
delete from public.activities legacy
using migrated_tasks
where legacy.id = migrated_tasks.id;

do $$
declare
  remaining_legacy_tasks bigint;
begin
  select count(*)
  into remaining_legacy_tasks
  from public.activities
  where kind = 'task';

  raise notice
    'Legacy tasks not backfilled because their parent/assignee could not be determined safely: %',
    remaining_legacy_tasks;
end;
$$;

commit;

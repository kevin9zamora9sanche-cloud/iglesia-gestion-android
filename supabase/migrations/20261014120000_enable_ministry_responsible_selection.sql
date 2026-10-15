begin;

create or replace function public.can_assign_active_user(
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
    from public.profiles target_profile
    join public.activities target_activity
      on target_activity.id = target_activity_id
    where target_profile.id = target_user_id
      and target_profile.is_active
      and (
        (select public.is_pastor())
        or (
          target_activity.ministry_id is not null
          and (select public.can_manage_ministry(target_activity.ministry_id))
          and exists (
            select 1
            from public.ministry_memberships target_membership
            where target_membership.user_id = target_profile.id
              and target_membership.ministry_id = target_activity.ministry_id
          )
        )
      )
  );
$$;

CREATE OR REPLACE FUNCTION public.can_view_profile_for_assignment(target_profile_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select public.is_active_user())
    and exists (
      select 1
      from public.profiles target_profile
      where target_profile.id = target_profile_id
        and target_profile.is_active
        and (
          (select public.is_pastor())
          or exists (
            select 1
            from public.ministry_memberships viewer_membership
            join public.ministry_memberships target_membership
              on target_membership.ministry_id = viewer_membership.ministry_id
            where viewer_membership.user_id = (select auth.uid())
              and target_membership.user_id = target_profile.id
          )
          or exists (
            select 1
            from public.activity_assignments viewer_assignment
            join public.activity_assignments target_assignment
              on target_assignment.activity_id = viewer_assignment.activity_id
            where viewer_assignment.user_id = (select auth.uid())
              and target_assignment.user_id = target_profile.id
          )
          or exists (
            select 1
            from public.projects related_project
            where (related_project.leader_id = (select auth.uid())
              or related_project.created_by = (select auth.uid()))
              and target_profile.id in (
                related_project.leader_id,
                related_project.created_by
              )
          )
        )
    );
$$;

revoke all on function public.can_view_profile_for_assignment(uuid)
  from public, anon;
grant execute on function public.can_view_profile_for_assignment(uuid)
  to authenticated;

DROP POLICY IF EXISTS "Active users can view active profiles in shared ministries" ON public.profiles;
create policy "Active users can view active profiles in shared ministries"
  on public.profiles for select to authenticated
  using (
    (select public.can_view_profile_for_assignment(id))
  );

CREATE OR REPLACE FUNCTION public.can_view_ministry_membership(
  target_user_id uuid,
  target_ministry_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select (select public.is_active_user())
    and exists (
      select 1
      from public.profiles target_profile
      where target_profile.id = target_user_id
        and target_profile.is_active
        and (
          (select public.is_pastor())
          or exists (
            select 1
            from public.ministry_memberships viewer_membership
            where viewer_membership.user_id = (select auth.uid())
              and viewer_membership.ministry_id = target_ministry_id
          )
        )
    );
$$;

revoke all on function public.can_view_ministry_membership(uuid, uuid)
  from public, anon;
grant execute on function public.can_view_ministry_membership(uuid, uuid)
  to authenticated;

DROP POLICY IF EXISTS "Members can view memberships in their ministries" ON public.ministry_memberships;
create policy "Members can view memberships in their ministries"
  on public.ministry_memberships for select to authenticated
  using (
    (select public.can_view_ministry_membership(user_id, ministry_id))
  );

commit;

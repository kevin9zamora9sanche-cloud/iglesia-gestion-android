begin;

revoke all on function public.create_assignment_notification()
  from public, anon, authenticated;
revoke all on function public.log_management_change()
  from public, anon, authenticated;

commit;

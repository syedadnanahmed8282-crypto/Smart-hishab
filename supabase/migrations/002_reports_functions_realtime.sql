-- রিপোর্ট ভিউ, ফাংশন ও রিয়েলটাইম
create view public.folder_summary with (security_invoker = true) as
select
  f.id as folder_id, f.user_id, f.name, f.status, f.start_date,
  coalesce(f.end_date, current_date) as period_end,
  (coalesce(f.end_date, current_date) - f.start_date + 1) as total_days,
  count(e.id) filter (where e.quantity > 0) as work_days,
  count(e.id) filter (where e.quantity = 0) as off_days,
  coalesce(sum(e.quantity), 0) as total_pieces,
  coalesce(sum(e.income), 0) as total_income,
  coalesce(sum(e.expense), 0) as total_expense,
  coalesce(sum(e.profit), 0) as net_profit,
  round(coalesce(sum(e.income), 0) / greatest(1, coalesce(f.end_date, current_date) - f.start_date + 1), 2) as avg_income_per_day,
  round(coalesce(sum(e.expense), 0) / greatest(1, coalesce(f.end_date, current_date) - f.start_date + 1), 2) as avg_expense_per_day,
  round(coalesce(sum(e.quantity), 0)::numeric / greatest(1, coalesce(f.end_date, current_date) - f.start_date + 1), 0) as avg_pieces_per_day,
  f.income_goal, f.expense_limit,
  round(100 * coalesce(sum(e.income), 0) / nullif(f.income_goal, 0), 1) as goal_pct,
  round(100 * coalesce(sum(e.expense), 0) / nullif(f.expense_limit, 0), 1) as limit_pct
from public.folders f
left join public.entries e on e.folder_id = f.id and e.deleted_at is null
where f.deleted_at is null
group by f.id;

create view public.weekly_summary with (security_invoker = true) as
select
  e.folder_id, e.user_id,
  ((e.entry_date - f.start_date) / 7) + 1 as week_no,
  f.start_date + (((e.entry_date - f.start_date) / 7) * 7) as week_start,
  least(f.start_date + (((e.entry_date - f.start_date) / 7) * 7) + 6, coalesce(f.end_date, current_date)) as week_end,
  count(*) filter (where e.quantity > 0) as work_days,
  count(*) filter (where e.quantity = 0) as off_days,
  sum(e.quantity) as pieces, sum(e.income) as income, sum(e.expense) as expense, sum(e.profit) as profit
from public.entries e
join public.folders f on f.id = e.folder_id
where e.deleted_at is null and f.deleted_at is null
group by e.folder_id, e.user_id, f.start_date, f.end_date, ((e.entry_date - f.start_date) / 7);

create view public.cycle_month_summary with (security_invoker = true) as
select
  e.folder_id, e.user_id,
  ((e.entry_date - f.start_date) / 30) + 1 as month_no,
  f.start_date + (((e.entry_date - f.start_date) / 30) * 30) as month_start,
  least(f.start_date + (((e.entry_date - f.start_date) / 30) * 30) + 29, coalesce(f.end_date, current_date)) as month_end,
  count(*) filter (where e.quantity > 0) as work_days,
  count(*) filter (where e.quantity = 0) as off_days,
  sum(e.quantity) as pieces, sum(e.income) as income, sum(e.expense) as expense, sum(e.profit) as profit
from public.entries e
join public.folders f on f.id = e.folder_id
where e.deleted_at is null and f.deleted_at is null
group by e.folder_id, e.user_id, f.start_date, f.end_date, ((e.entry_date - f.start_date) / 30);

revoke all on public.folder_summary, public.weekly_summary, public.cycle_month_summary from anon;
grant select on public.folder_summary, public.weekly_summary, public.cycle_month_summary to authenticated;

create function public.snapshot_weekly_reports(p_folder uuid) returns integer
language plpgsql set search_path = '' as $$
declare f public.folders; n integer;
begin
  select * into f from public.folders where id = p_folder and deleted_at is null;
  if not found then raise exception 'ফোল্ডার পাওয়া যায়নি'; end if;
  insert into public.weekly_reports
    (user_id, folder_id, week_no, start_date, end_date, is_partial,
     total_pieces, total_income, total_expense, work_days, off_days)
  select f.user_id, f.id, w.week_no, w.week_start, w.week_end,
         (w.week_end < w.week_start + 6),
         w.pieces, w.income, w.expense, w.work_days, w.off_days
  from public.weekly_summary w
  where w.folder_id = f.id
    and (f.status = 'closed' or w.week_start + 6 < current_date)
  on conflict (folder_id, week_no) do update set
    start_date = excluded.start_date, end_date = excluded.end_date, is_partial = excluded.is_partial,
    total_pieces = excluded.total_pieces, total_income = excluded.total_income,
    total_expense = excluded.total_expense, work_days = excluded.work_days, off_days = excluded.off_days;
  get diagnostics n = row_count;
  return n;
end $$;

create function public.close_folder(p_folder uuid, p_end date default null) returns public.folders
language plpgsql set search_path = '' as $$
declare r public.folders;
begin
  update public.folders set status = 'closed', end_date = p_end
   where id = p_folder and deleted_at is null and status = 'running'
   returning * into r;
  if not found then raise exception 'ফোল্ডার চালু অবস্থায় নেই বা পাওয়া যায়নি'; end if;
  perform public.snapshot_weekly_reports(p_folder);
  return r;
end $$;

create function public.reopen_folder(p_folder uuid) returns public.folders
language plpgsql set search_path = '' as $$
declare r public.folders;
begin
  update public.folders set status = 'running'
   where id = p_folder and deleted_at is null and status = 'closed'
   returning * into r;
  if not found then raise exception 'ফোল্ডার সমাপ্ত অবস্থায় নেই বা পাওয়া যায়নি'; end if;
  return r;
end $$;

create function public.purge_deleted(p_days integer default 30) returns integer
language plpgsql set search_path = '' as $$
declare a integer; b integer;
begin
  perform set_config('app.bypass_guard', 'on', true);
  delete from public.entries where deleted_at < now() - make_interval(days => p_days);
  get diagnostics a = row_count;
  delete from public.folders where deleted_at < now() - make_interval(days => p_days);
  get diagnostics b = row_count;
  perform set_config('app.bypass_guard', 'off', true);
  return a + b;
end $$;

revoke execute on function public.snapshot_weekly_reports(uuid), public.close_folder(uuid, date), public.reopen_folder(uuid), public.purge_deleted(integer) from public, anon;
grant execute on function public.snapshot_weekly_reports(uuid), public.close_folder(uuid, date), public.reopen_folder(uuid), public.purge_deleted(integer) to authenticated;

alter publication supabase_realtime add table public.folders, public.entries, public.weekly_reports, public.user_settings;

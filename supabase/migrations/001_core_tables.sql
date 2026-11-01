-- স্মার্ট হিসাব: মূল টেবিল, তালা, ট্রিগার ও RLS
create table public.user_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  viewer_mode boolean not null default false,
  viewer_pin_hash text,
  report_mode text not null default 'cycle' check (report_mode in ('cycle','calendar')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.folders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 80),
  start_date date not null,
  expected_end_date date,
  end_date date,
  status text not null default 'running' check (status in ('running','closed')),
  closed_at timestamptz,
  income_goal numeric(12,2) check (income_goal is null or income_goal >= 0),
  expense_limit numeric(12,2) check (expense_limit is null or expense_limit >= 0),
  wage_rate numeric(10,2) not null default 50 check (wage_rate >= 0),
  daily_target_pcs integer check (daily_target_pcs is null or daily_target_pcs >= 0),
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint folders_id_user_unique unique (id, user_id),
  constraint folders_dates_ok check (end_date is null or end_date >= start_date),
  constraint folders_expected_ok check (expected_end_date is null or expected_end_date >= start_date),
  constraint folders_closed_has_end check (status <> 'closed' or end_date is not null),
  constraint folders_running_no_end check (status = 'closed' or end_date is null)
);

create table public.entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  folder_id uuid not null,
  entry_date date not null,
  quantity integer not null default 0 check (quantity >= 0),
  expense numeric(12,2) not null default 0 check (expense >= 0),
  rate_per_100 numeric(10,2) not null default 50 check (rate_per_100 >= 0),
  note text,
  income numeric(14,2) generated always as (round(quantity * rate_per_100 / 100.0, 2)) stored,
  profit numeric(14,2) generated always as (round(quantity * rate_per_100 / 100.0, 2) - expense) stored,
  is_off_day boolean generated always as (quantity = 0) stored,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint entries_folder_fk foreign key (folder_id, user_id)
    references public.folders (id, user_id) on delete cascade
);
create unique index entries_one_per_day on public.entries (folder_id, entry_date) where deleted_at is null;
create index entries_folder_date_idx on public.entries (folder_id, entry_date);
create index entries_user_updated_idx on public.entries (user_id, updated_at);
create index folders_user_updated_idx on public.folders (user_id, updated_at);

create table public.weekly_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  folder_id uuid not null,
  week_no integer not null check (week_no >= 1),
  start_date date not null,
  end_date date not null,
  is_partial boolean not null default false,
  total_pieces bigint not null default 0,
  total_income numeric(14,2) not null default 0,
  total_expense numeric(14,2) not null default 0,
  net_profit numeric(14,2) generated always as (total_income - total_expense) stored,
  work_days integer not null default 0,
  off_days integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint weekly_reports_folder_fk foreign key (folder_id, user_id)
    references public.folders (id, user_id) on delete cascade,
  constraint weekly_reports_unique unique (folder_id, week_no)
);
create index weekly_reports_folder_idx on public.weekly_reports (folder_id);

-- updated_at
create function public.set_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin new.updated_at := now(); return new; end $$;
create trigger trg_user_settings_updated before update on public.user_settings for each row execute function public.set_updated_at();
create trigger trg_folders_updated before update on public.folders for each row execute function public.set_updated_at();
create trigger trg_entries_updated before update on public.entries for each row execute function public.set_updated_at();
create trigger trg_weekly_reports_updated before update on public.weekly_reports for each row execute function public.set_updated_at();

-- ফোল্ডার চালু/সমাপ্ত অবস্থার নিয়ম
create function public.folders_state_guard() returns trigger
language plpgsql set search_path = '' as $$
begin
  if old.status = 'running' and new.status = 'closed' then
    new.closed_at := coalesce(new.closed_at, now());
    new.end_date := coalesce(
      new.end_date,
      (select max(e.entry_date) from public.entries e where e.folder_id = new.id and e.deleted_at is null),
      current_date);
    if new.end_date < new.start_date then new.end_date := new.start_date; end if;
  elsif old.status = 'closed' and new.status = 'running' then
    new.end_date := null;
    new.closed_at := null;
  elsif old.status = 'closed' and new.status = 'closed' then
    if new.start_date is distinct from old.start_date or new.end_date is distinct from old.end_date then
      raise exception 'সমাপ্ত ফোল্ডারের তারিখ বদলানো যাবে না, আগে আবার চালু করুন';
    end if;
  end if;
  return new;
end $$;
create trigger trg_folders_state before update on public.folders for each row execute function public.folders_state_guard();

-- সমাপ্ত ফোল্ডারে এন্ট্রি যোগ, সম্পাদনা বা মোছা বন্ধ
create function public.entries_guard() returns trigger
language plpgsql set search_path = '' as $$
declare f record;
begin
  if coalesce(current_setting('app.bypass_guard', true), 'off') = 'on' then
    if tg_op = 'DELETE' then return old; else return new; end if;
  end if;
  select status, deleted_at, start_date into f
    from public.folders where id = coalesce(new.folder_id, old.folder_id);
  if found and f.status = 'closed' and f.deleted_at is null then
    raise exception 'ফোল্ডার সমাপ্ত: এন্ট্রি যোগ, সম্পাদনা বা মোছা যাবে না';
  end if;
  if tg_op in ('INSERT', 'UPDATE') then
    if found and new.entry_date < f.start_date then
      raise exception 'তারিখ ফোল্ডারের শুরুর তারিখের আগে হতে পারে না';
    end if;
    return new;
  end if;
  return old;
end $$;
create trigger trg_entries_guard before insert or update or delete on public.entries for each row execute function public.entries_guard();

-- ফোল্ডার মুছলে (soft delete) তার এন্ট্রিও মুছবে, ফেরালে ফিরবে
create function public.folders_cascade_soft_delete() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.deleted_at is distinct from old.deleted_at then
    perform set_config('app.bypass_guard', 'on', true);
    if new.deleted_at is not null then
      update public.entries set deleted_at = new.deleted_at where folder_id = new.id and deleted_at is null;
    else
      update public.entries set deleted_at = null where folder_id = new.id and deleted_at = old.deleted_at;
    end if;
    perform set_config('app.bypass_guard', 'off', true);
  end if;
  return null;
end $$;
create trigger trg_folders_cascade after update of deleted_at on public.folders for each row execute function public.folders_cascade_soft_delete();

-- নতুন ব্যবহারকারীর সেটিংস সারি
create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.user_settings (user_id, display_name)
  values (new.id, nullif(btrim(coalesce(new.raw_user_meta_data ->> 'name', '')), ''))
  on conflict (user_id) do nothing;
  return new;
end $$;
revoke execute on function public.handle_new_user() from public, anon, authenticated;
create trigger trg_on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

-- RLS: প্রত্যেকে শুধু নিজের তথ্য দেখে ও বদলায়
alter table public.user_settings enable row level security;
create policy user_settings_select on public.user_settings for select to authenticated using ((select auth.uid()) = user_id);
create policy user_settings_insert on public.user_settings for insert to authenticated with check ((select auth.uid()) = user_id);
create policy user_settings_update on public.user_settings for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
revoke all on public.user_settings from anon;

do $$
declare t text;
begin
  foreach t in array array['folders','entries','weekly_reports'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('create policy %I on public.%I for select to authenticated using ((select auth.uid()) = user_id)', t || '_select', t);
    execute format('create policy %I on public.%I for insert to authenticated with check ((select auth.uid()) = user_id)', t || '_insert', t);
    execute format('create policy %I on public.%I for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id)', t || '_update', t);
    execute format('create policy %I on public.%I for delete to authenticated using ((select auth.uid()) = user_id)', t || '_delete', t);
    execute format('revoke all on public.%I from anon', t);
  end loop;
end $$;

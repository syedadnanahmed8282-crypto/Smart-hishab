-- তিন ধরনের হিসাব: main (মালের হিসাব), alt (দৈনিক খরচ), prem (আয়-ব্যয়)
-- আগের সব ফোল্ডার ও এন্ট্রি নিজে থেকে 'main' হয়ে যায়; কিছু মোছা বা বদলানো হয় না।
alter table public.folders add column mode text not null default 'main' check (mode in ('main','alt','prem'));
alter table public.entries add column mode text not null default 'main' check (mode in ('main','alt','prem'));
alter table public.entries add column kind text not null default 'expense' check (kind in ('income','expense'));
alter table public.entries add column category text check (category is null or char_length(btrim(category)) between 1 and 40);
alter table public.entries add column amount numeric not null default 0 check (amount >= 0);
create index folders_user_mode_idx on public.folders (user_id, mode);

-- একদিনে একটাই এন্ট্রি: শুধু মালের হিসাবে। দৈনিক খরচ ও আয়-ব্যয়তে একদিনে একাধিক এন্ট্রি চলে।
drop index public.entries_one_per_day;
create unique index entries_one_per_day on public.entries (folder_id, entry_date) where deleted_at is null and mode = 'main';

create or replace function public.push_folders(p_rows jsonb) returns jsonb
language plpgsql set search_path = '' as $$
declare
  r jsonb; cur public.folders; rid uuid; base integer;
  ok integer := 0; conflicts jsonb := '[]'::jsonb; errs jsonb := '[]'::jsonb;
begin
  for r in select value from jsonb_array_elements(coalesce(p_rows, '[]'::jsonb)) loop
    rid := (r ->> 'id')::uuid;
    begin
      select * into cur from public.folders where id = rid;
      if not found then
        insert into public.folders (id, user_id, name, start_date, expected_end_date, end_date, status,
                                    income_goal, expense_limit, wage_rate, daily_target_pcs, deleted_at, mode)
        values (rid, auth.uid(), r ->> 'name', (r ->> 'start_date')::date,
                nullif(r ->> 'expected_end_date', '')::date, nullif(r ->> 'end_date', '')::date,
                coalesce(r ->> 'status', 'running'),
                nullif(r ->> 'income_goal', '')::numeric, nullif(r ->> 'expense_limit', '')::numeric,
                coalesce(nullif(r ->> 'wage_rate', '')::numeric, 50),
                nullif(r ->> 'daily_target_pcs', '')::integer, nullif(r ->> 'deleted_at', '')::timestamptz,
                coalesce(nullif(r ->> 'mode', ''), 'main'));
        ok := ok + 1;
      else
        base := coalesce(nullif(r ->> 'base_version', '')::integer, 0);
        if base > 0 and base <> cur.version then
          conflicts := conflicts || to_jsonb(rid::text);
        else
          update public.folders set
            name = r ->> 'name', start_date = (r ->> 'start_date')::date,
            expected_end_date = nullif(r ->> 'expected_end_date', '')::date,
            end_date = nullif(r ->> 'end_date', '')::date,
            status = coalesce(r ->> 'status', 'running'),
            income_goal = nullif(r ->> 'income_goal', '')::numeric,
            expense_limit = nullif(r ->> 'expense_limit', '')::numeric,
            wage_rate = coalesce(nullif(r ->> 'wage_rate', '')::numeric, 50),
            daily_target_pcs = nullif(r ->> 'daily_target_pcs', '')::integer,
            deleted_at = nullif(r ->> 'deleted_at', '')::timestamptz,
            mode = coalesce(nullif(r ->> 'mode', ''), cur.mode)
          where id = rid;
          ok := ok + 1;
        end if;
      end if;
    exception when others then
      errs := errs || jsonb_build_array(jsonb_build_object('id', rid::text, 'msg', sqlerrm));
    end;
  end loop;
  return jsonb_build_object('ok', ok, 'conflicts', conflicts, 'errors', errs);
end $$;

create or replace function public.push_entries(p_rows jsonb) returns jsonb
language plpgsql set search_path = '' as $$
declare
  r jsonb; cur public.entries; rid uuid; base integer;
  ok integer := 0; conflicts jsonb := '[]'::jsonb; errs jsonb := '[]'::jsonb;
begin
  for r in select value from jsonb_array_elements(coalesce(p_rows, '[]'::jsonb)) loop
    rid := (r ->> 'id')::uuid;
    begin
      select * into cur from public.entries where id = rid;
      if not found then
        insert into public.entries (id, user_id, folder_id, entry_date, quantity, expense, rate_per_100, note, deleted_at, mode, kind, category, amount)
        values (rid, auth.uid(), (r ->> 'folder_id')::uuid, (r ->> 'entry_date')::date,
                coalesce(nullif(r ->> 'quantity', '')::integer, 0), coalesce(nullif(r ->> 'expense', '')::numeric, 0),
                coalesce(nullif(r ->> 'rate_per_100', '')::numeric, 0),
                r ->> 'note', nullif(r ->> 'deleted_at', '')::timestamptz,
                coalesce(nullif(r ->> 'mode', ''), 'main'), coalesce(nullif(r ->> 'kind', ''), 'expense'),
                nullif(r ->> 'category', ''), coalesce(nullif(r ->> 'amount', '')::numeric, 0));
        ok := ok + 1;
      else
        base := coalesce(nullif(r ->> 'base_version', '')::integer, 0);
        if base > 0 and base <> cur.version then
          conflicts := conflicts || to_jsonb(rid::text);
        else
          update public.entries set
            folder_id = (r ->> 'folder_id')::uuid, entry_date = (r ->> 'entry_date')::date,
            quantity = coalesce(nullif(r ->> 'quantity', '')::integer, 0), expense = coalesce(nullif(r ->> 'expense', '')::numeric, 0),
            rate_per_100 = coalesce(nullif(r ->> 'rate_per_100', '')::numeric, 0), note = r ->> 'note',
            deleted_at = nullif(r ->> 'deleted_at', '')::timestamptz,
            mode = coalesce(nullif(r ->> 'mode', ''), cur.mode), kind = coalesce(nullif(r ->> 'kind', ''), cur.kind),
            category = case when r ? 'category' then nullif(r ->> 'category', '') else cur.category end,
            amount = coalesce(nullif(r ->> 'amount', '')::numeric, cur.amount)
          where id = rid;
          ok := ok + 1;
        end if;
      end if;
    exception when others then
      errs := errs || jsonb_build_array(jsonb_build_object('id', rid::text, 'msg', sqlerrm));
    end;
  end loop;
  return jsonb_build_object('ok', ok, 'conflicts', conflicts, 'errors', errs);
end $$;

-- সংস্করণ নম্বর ও সংঘর্ষ-নিরাপদ আপলোড
alter table public.folders add column version integer not null default 1;
alter table public.entries add column version integer not null default 1;

create function public.bump_version() returns trigger
language plpgsql set search_path = '' as $$
begin new.version := old.version + 1; return new; end $$;
create trigger trg_folders_bump before update on public.folders for each row execute function public.bump_version();
create trigger trg_entries_bump before update on public.entries for each row execute function public.bump_version();

-- ফোন বলে "আমি সংস্করণ N দেখে বদলেছি"। সার্ভারে সংস্করণ এগিয়ে গেলে ওই সারি নেওয়া হয় না (সার্ভারের তথ্য জেতে)।
-- একটা সারির ভুলে বাকিগুলো আটকায় না।
create function public.push_folders(p_rows jsonb) returns jsonb
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
                                    income_goal, expense_limit, wage_rate, daily_target_pcs, deleted_at)
        values (rid, auth.uid(), r ->> 'name', (r ->> 'start_date')::date,
                nullif(r ->> 'expected_end_date', '')::date, nullif(r ->> 'end_date', '')::date,
                coalesce(r ->> 'status', 'running'),
                nullif(r ->> 'income_goal', '')::numeric, nullif(r ->> 'expense_limit', '')::numeric,
                coalesce(nullif(r ->> 'wage_rate', '')::numeric, 50),
                nullif(r ->> 'daily_target_pcs', '')::integer, nullif(r ->> 'deleted_at', '')::timestamptz);
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
            deleted_at = nullif(r ->> 'deleted_at', '')::timestamptz
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

create function public.push_entries(p_rows jsonb) returns jsonb
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
        insert into public.entries (id, user_id, folder_id, entry_date, quantity, expense, rate_per_100, note, deleted_at)
        values (rid, auth.uid(), (r ->> 'folder_id')::uuid, (r ->> 'entry_date')::date,
                (r ->> 'quantity')::integer, (r ->> 'expense')::numeric, (r ->> 'rate_per_100')::numeric,
                r ->> 'note', nullif(r ->> 'deleted_at', '')::timestamptz);
        ok := ok + 1;
      else
        base := coalesce(nullif(r ->> 'base_version', '')::integer, 0);
        if base > 0 and base <> cur.version then
          conflicts := conflicts || to_jsonb(rid::text);
        else
          update public.entries set
            folder_id = (r ->> 'folder_id')::uuid, entry_date = (r ->> 'entry_date')::date,
            quantity = (r ->> 'quantity')::integer, expense = (r ->> 'expense')::numeric,
            rate_per_100 = (r ->> 'rate_per_100')::numeric, note = r ->> 'note',
            deleted_at = nullif(r ->> 'deleted_at', '')::timestamptz
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

revoke execute on function public.push_folders(jsonb), public.push_entries(jsonb) from public, anon;
grant execute on function public.push_folders(jsonb), public.push_entries(jsonb) to authenticated;

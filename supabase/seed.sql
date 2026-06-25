-- Optional local seed data. Runs after migrations on `supabase db reset`.
-- Keep this to globally-readable reference rows only — user-owned tables are
-- gated by RLS and require a real auth.users row + JWT to populate meaningfully.

-- A few common foods in the shared cache so search/barcode demos work offline.
insert into foods (name, brand, serving_qty, serving_unit, calories, protein_g, carbs_g, fat_g, source, external_id)
values
  ('Banana',            null,        100, 'g', 89,  1.1, 22.8, 0.3, 'usda', 'usda:banana'),
  ('Chicken breast',    null,        100, 'g', 165, 31,  0,    3.6, 'usda', 'usda:chicken_breast'),
  ('White rice, cooked',null,        100, 'g', 130, 2.7, 28,   0.3, 'usda', 'usda:white_rice_cooked'),
  ('Whole milk',        null,        100, 'ml',61,  3.2, 4.8,  3.3, 'usda', 'usda:whole_milk')
on conflict (source, external_id) do nothing;

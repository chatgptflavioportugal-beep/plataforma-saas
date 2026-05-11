-- Seed: 0002_super_admin.sql
-- Promove usuário para SUPER_ADMIN
-- Substitua o email pelo do administrador da plataforma.
-- Execute após criar o usuário via Supabase Auth.

-- UPDATE public.user_profiles
-- SET system_role = 'SUPER_ADMIN'
-- WHERE id = (
--     SELECT id FROM auth.users WHERE email = 'admin@suaplataforma.com'
-- );

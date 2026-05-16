-- Migration: 0014_fix_handle_new_user.sql
-- Remove a criação automática de tenant individual no registro.
--
-- Comportamento anterior (incorreto):
--   O trigger criava automaticamente um tenant individual usando o nome
--   do Gmail ou prefixo do e-mail, pulando a tela de onboarding e
--   enviando o usuário direto para o dashboard com um contexto que ele
--   nunca escolheu.
--
-- Comportamento novo (correto):
--   O trigger cria APENAS o user_profile.
--   O tenant individual (ou empresarial) é criado somente após ação
--   explícita do usuário na tela de onboarding:
--     - POST /api/v1/public/individual-tenant
--     - POST /api/v1/public/onboarding
--
-- DOWN (reverter):
--   Reaplique a função da migration 0013.

CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.user_profiles (id, full_name, avatar_url)
    VALUES (
        NEW.id,
        NEW.raw_user_meta_data ->> 'full_name',
        NEW.raw_user_meta_data ->> 'avatar_url'
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

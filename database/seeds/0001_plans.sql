-- Seed: 0001_plans.sql
-- Planos base do SaaS (com preços anuais e versionamento)

INSERT INTO plans (code, name, description, price_monthly, price_annual, discount_annual_percent, max_users, max_ai_requests_month, sort_order, version, is_current_version, billing_type, is_most_popular)
VALUES
(
    'free',
    'Free',
    'Plano gratuito com funcionalidades básicas',
    0.00, 0.00, 0, 3, 50,
    1, 1, TRUE, 'both', FALSE
),
(
    'starter',
    'Starter',
    'Para pequenas equipes que precisam de mais recursos',
    49.90, 39.90, 20, 5, 200,
    2, 1, TRUE, 'both', FALSE
),
(
    'pro',
    'Pro',
    'Para equipes em crescimento com uso intenso',
    99.90, 79.90, 20, 25, 1000,
    3, 1, TRUE, 'both', TRUE
),
(
    'enterprise',
    'Enterprise',
    'Solução completa para grandes organizações',
    299.90, 239.90, 20, -1, -1,
    4, 1, TRUE, 'both', FALSE
)
ON CONFLICT (code, version) DO NOTHING;

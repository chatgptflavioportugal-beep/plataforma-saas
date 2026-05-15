-- Seed: 0001_plans.sql
-- Planos base do SaaS (com preços anuais e versionamento)

INSERT INTO plans (code, name, description, price_monthly, price_annual, discount_annual_percent, max_users, max_ai_requests_month, features, sort_order, version, is_current_version, billing_type)
VALUES
(
    'free',
    'Free',
    'Plano gratuito com funcionalidades básicas',
    0.00,
    0.00,
    0,
    3,
    50,
    '{
        "pdf.merge": true,
        "reports.view": true,
        "reports.export": false,
        "ai.agents": false,
        "api.access": false,
        "white_label": false,
        "priority_support": false,
        "max_users": 3,
        "max_ai_requests_month": 50,
        "max_pdf_merges_month": 10
    }',
    1,
    1,
    TRUE,
    'both'
),
(
    'starter',
    'Starter',
    'Para pequenas equipes que precisam de mais recursos',
    49.90,
    39.90,
    20,
    5,
    200,
    '{
        "pdf.merge": true,
        "reports.view": true,
        "reports.export": false,
        "ai.agents": true,
        "api.access": false,
        "white_label": false,
        "priority_support": false,
        "max_users": 5,
        "max_ai_requests_month": 200,
        "max_pdf_merges_month": 100
    }',
    2,
    1,
    TRUE,
    'both'
),
(
    'pro',
    'Pro',
    'Para equipes em crescimento com uso intenso',
    99.90,
    79.90,
    20,
    25,
    1000,
    '{
        "pdf.merge": true,
        "reports.view": true,
        "reports.export": true,
        "ai.agents": true,
        "api.access": true,
        "white_label": false,
        "priority_support": true,
        "max_users": 25,
        "max_ai_requests_month": 1000,
        "max_pdf_merges_month": -1
    }',
    3,
    1,
    TRUE,
    'both'
),
(
    'enterprise',
    'Enterprise',
    'Solução completa para grandes organizações',
    299.90,
    239.90,
    20,
    -1,
    -1,
    '{
        "pdf.merge": true,
        "reports.view": true,
        "reports.export": true,
        "ai.agents": true,
        "api.access": true,
        "white_label": true,
        "priority_support": true,
        "max_users": -1,
        "max_ai_requests_month": -1,
        "max_pdf_merges_month": -1
    }',
    4,
    1,
    TRUE,
    'both'
)
ON CONFLICT (code, version) DO NOTHING;

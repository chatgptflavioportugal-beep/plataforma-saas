-- Seed: 0001_plans.sql
-- Planos base do SaaS

INSERT INTO plans (code, name, description, price_monthly, max_users, max_ai_requests_month, features, sort_order)
VALUES
(
    'free',
    'Free',
    'Plano gratuito com funcionalidades básicas',
    0.00,
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
    1
),
(
    'starter',
    'Starter',
    'Para pequenas equipes que precisam de mais recursos',
    29.90,
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
    2
),
(
    'pro',
    'Pro',
    'Para equipes em crescimento com uso intenso',
    99.90,
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
    3
),
(
    'enterprise',
    'Enterprise',
    'Solução completa para grandes organizações',
    299.90,
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
    4
)
ON CONFLICT (code) DO NOTHING;

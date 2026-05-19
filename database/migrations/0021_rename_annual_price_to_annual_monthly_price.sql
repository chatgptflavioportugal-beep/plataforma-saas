-- Migration: 0021_rename_annual_price_to_annual_monthly_price.sql
-- Renomeia annual_price para annual_monthly_price em plan_version_modules
-- O campo representa o valor mensal equivalente quando o cliente escolhe pagamento anual
-- O total anual real é calculado como annual_monthly_price * 12

-- DOWN:
-- ALTER TABLE plan_version_modules RENAME COLUMN annual_monthly_price TO annual_price;

ALTER TABLE plan_version_modules
    RENAME COLUMN annual_price TO annual_monthly_price;

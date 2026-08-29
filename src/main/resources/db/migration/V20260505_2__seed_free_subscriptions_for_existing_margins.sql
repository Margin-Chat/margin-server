INSERT INTO subscriptions (margins, tier, status, created_at, updated_at)
SELECT m.margin_id, 'FREE', 'ACTIVE', NOW(), NOW()
FROM margins m
WHERE NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.margins = m.margin_id);

INSERT INTO subscription_limits (subscription_id)
SELECT s.id
FROM subscriptions s
WHERE NOT EXISTS (SELECT 1 FROM subscription_limits sl WHERE sl.subscription_id = s.id);
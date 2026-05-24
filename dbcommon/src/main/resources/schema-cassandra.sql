-- Safe for re-run: IF NOT EXISTS only (no DROP).
-- Local dev: SimpleStrategy RF=1. Adjust for production.

CREATE KEYSPACE IF NOT EXISTS delivery_booking
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS delivery_booking.delivery_opportunity_claims_timeline (
    driver_id uuid,
    requested_at timeuuid,
    opportunity_id uuid,
    slot text,
    metadata text,

    PRIMARY KEY ((driver_id), requested_at)
)
WITH CLUSTERING ORDER BY (requested_at DESC)
  AND default_time_to_live = 2592000;

CREATE TABLE IF NOT EXISTS delivery_booking.delivery_opportunity_claims (
    driver_id uuid,
    opportunity_id uuid,
    slot text,
    requested_at timeuuid,
    metadata text,

    PRIMARY KEY ((driver_id), opportunity_id)
)
WITH default_time_to_live = 2592000;

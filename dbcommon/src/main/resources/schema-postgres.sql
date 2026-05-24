CREATE TABLE IF NOT EXISTS delivery_opportunities (
  id UUID PRIMARY KEY,
  region_id INT NOT NULL,
  zone_id INT NOT NULL,
  booking_window_start TIMESTAMPTZ NOT NULL,
  booking_window_end TIMESTAMPTZ NOT NULL,
  capacity INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS delivery_opportunity_slots (
  id UUID PRIMARY KEY,
  opportunity_id UUID NOT NULL REFERENCES delivery_opportunities(id),
  opportunity_slot INT NOT NULL,
  claimed_by UUID,
  claimed_at TIMESTAMPTZ,
  version INT NOT NULL DEFAULT 1,
  UNIQUE (opportunity_id, opportunity_slot)
);

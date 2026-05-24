CREATE TABLE IF NOT EXISTS delivery_opportunities (
  id UUID PRIMARY KEY,
  region_id INT NOT NULL,
  zone_id INT NOT NULL,
  booking_window_start TIMESTAMP WITH TIME ZONE NOT NULL,
  booking_window_end TIMESTAMP WITH TIME ZONE NOT NULL,
  capacity INT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS delivery_opportunity_slots (
  id UUID PRIMARY KEY,
  opportunity_id UUID NOT NULL,
  opportunity_slot INT NOT NULL,
  claimed_by UUID,
  claimed_at TIMESTAMP WITH TIME ZONE,
  version INT NOT NULL DEFAULT 1,
  UNIQUE (opportunity_id, opportunity_slot),
  FOREIGN KEY (opportunity_id) REFERENCES delivery_opportunities(id)
);

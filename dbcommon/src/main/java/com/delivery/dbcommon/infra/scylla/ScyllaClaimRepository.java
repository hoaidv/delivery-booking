package com.delivery.dbcommon.infra.scylla;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.delivery.dbcommon.models.DeliveryOpportunityClaim;
import com.delivery.dbcommon.repository.DeliveryOpportunityClaimRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ScyllaClaimRepository implements DeliveryOpportunityClaimRepository {

	private static final int DEFAULT_PAGE_SIZE = 10;

	private final CqlSession session;
	private final PreparedStatement insertTimeline;
	private final PreparedStatement upsertLookup;
	private final PreparedStatement selectTimelineByDriver;
	private final PreparedStatement selectTimelineByDriverBefore;
	private final PreparedStatement selectLookup;

	public ScyllaClaimRepository(CqlSession session) {
		this.session = session;
		insertTimeline = session.prepare("""
				INSERT INTO delivery_booking.delivery_opportunity_claims_timeline
					(driver_id, requested_at, opportunity_id, slot, metadata)
				VALUES (?, ?, ?, ?, ?)
				""");

		upsertLookup = session.prepare("""
				INSERT INTO delivery_booking.delivery_opportunity_claims
					(driver_id, opportunity_id, slot, requested_at, metadata)
				VALUES (?, ?, ?, ?, ?)
				""");

		selectTimelineByDriver = session.prepare("""
				SELECT driver_id, requested_at, opportunity_id, slot, metadata
				FROM delivery_booking.delivery_opportunity_claims_timeline
				WHERE driver_id = ?
				LIMIT ?
				""");

		selectTimelineByDriverBefore = session.prepare("""
				SELECT driver_id, requested_at, opportunity_id, slot, metadata
				FROM delivery_booking.delivery_opportunity_claims_timeline
				WHERE driver_id = ?
				  AND requested_at < ?
				LIMIT ?
				""");

		selectLookup = session.prepare("""
				SELECT driver_id, opportunity_id, slot, requested_at, metadata
				FROM delivery_booking.delivery_opportunity_claims
				WHERE driver_id = ?
				  AND opportunity_id = ?
				""");
	}

	@Override
	public void insertTimeline(DeliveryOpportunityClaim claim) {
		session.execute(insertTimeline.bind(
				claim.driverId(),
				claim.requestedAt(),
				claim.opportunityId(),
				claim.slot(),
				claim.metadata()));
	}

	@Override
	public void upsertLookup(DeliveryOpportunityClaim claim) {
		session.execute(upsertLookup.bind(
				claim.driverId(),
				claim.opportunityId(),
				claim.slot(),
				claim.requestedAt(),
				claim.metadata()));
	}

	@Override
	public List<DeliveryOpportunityClaim> findByDriverId(UUID driverId) {
		return findByDriverIdAndRequestedAtRange(driverId, Optional.empty(), DEFAULT_PAGE_SIZE);
	}

	@Override
	public List<DeliveryOpportunityClaim> findByDriverIdAndRequestedAtRange(
			UUID driverId, Optional<UUID> beforeRequestedAt, int limit) {
		ResultSet resultSet = beforeRequestedAt
				.map(before -> session.execute(
						selectTimelineByDriverBefore.bind(driverId, before, limit)))
				.orElseGet(() -> session.execute(selectTimelineByDriver.bind(driverId, limit)));
		return mapRows(resultSet);
	}

	@Override
	public Optional<DeliveryOpportunityClaim> findByDriverIdAndOpportunityId(
			UUID driverId, UUID opportunityId) {
		ResultSet resultSet = session.execute(selectLookup.bind(driverId, opportunityId));
		Row row = resultSet.one();
		return row == null ? Optional.empty() : Optional.of(mapRow(row));
	}

	private static List<DeliveryOpportunityClaim> mapRows(ResultSet resultSet) {
		List<DeliveryOpportunityClaim> claims = new ArrayList<>();
		for (Row row : resultSet) {
			claims.add(mapRow(row));
		}
		return claims;
	}

	private static DeliveryOpportunityClaim mapRow(Row row) {
		return new DeliveryOpportunityClaim(
				row.getUuid("driver_id"),
				row.getUuid("opportunity_id"),
				row.getString("slot"),
				row.getUuid("requested_at"),
				row.getString("metadata"));
	}
}

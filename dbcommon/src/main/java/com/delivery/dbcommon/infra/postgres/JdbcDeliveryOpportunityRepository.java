package com.delivery.dbcommon.infra.postgres;

import com.delivery.dbcommon.models.DeliveryOpportunity;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcDeliveryOpportunityRepository implements DeliveryOpportunityRepository {

	private static final String INSERT_OPPORTUNITY = """
			INSERT INTO delivery_opportunities (
			    id, region_id, zone_id, booking_window_start, booking_window_end, capacity, created_at
			) VALUES (?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String SELECT_OPPORTUNITY_BY_ID = """
			SELECT id, region_id, zone_id, booking_window_start, booking_window_end, capacity, created_at
			FROM delivery_opportunities
			WHERE id = ?
			""";

	private static final String SELECT_ALL_OPPORTUNITIES = """
			SELECT id, region_id, zone_id, booking_window_start, booking_window_end, capacity, created_at
			FROM delivery_opportunities
			ORDER BY created_at DESC
			""";

	private static final String UPDATE_OPPORTUNITY = """
			UPDATE delivery_opportunities
			SET region_id = ?, zone_id = ?, booking_window_start = ?, booking_window_end = ?, capacity = ?
			WHERE id = ?
			""";

	private static final String INSERT_SLOT = """
			INSERT INTO delivery_opportunity_slots (
			    id, opportunity_id, opportunity_slot, claimed_by, claimed_at
			) VALUES (?, ?, ?, ?, ?)
			""";

	private static final String UPDATE_SLOT = """
			UPDATE delivery_opportunity_slots
			SET claimed_by = ?, claimed_at = ?, version = version + 1
			WHERE id = ?
			""";

	private static final String SELECT_SLOTS_BY_OPPORTUNITY = """
			SELECT id, opportunity_id, opportunity_slot, claimed_by, claimed_at, version
			FROM delivery_opportunity_slots
			WHERE opportunity_id = ?
			ORDER BY opportunity_slot
			""";

	private static final String SELECT_SLOT_BY_OPPORTUNITY_AND_SLOT = """
			SELECT id, opportunity_id, opportunity_slot, claimed_by, claimed_at, version
			FROM delivery_opportunity_slots
			WHERE opportunity_id = ?
			  AND opportunity_slot = ?
			""";

	private static final String CLAIM_SLOT_IF_UNCLAIMED = """
			UPDATE delivery_opportunity_slots
			SET claimed_by = ?, claimed_at = ?, version = version + 1
			WHERE opportunity_id = ?
			  AND opportunity_slot = ?
			  AND claimed_by IS NULL
			RETURNING id, opportunity_id, opportunity_slot, claimed_by, claimed_at, version
			""";

	private static final String DELETE_UNCLAIMED_SLOTS_ABOVE = """
			DELETE FROM delivery_opportunity_slots
			WHERE opportunity_id = ?
			  AND opportunity_slot > ?
			  AND claimed_by IS NULL
			""";

	private static final String COUNT_CLAIMED_SLOTS_ABOVE = """
			SELECT COUNT(*)
			FROM delivery_opportunity_slots
			WHERE opportunity_id = ?
			  AND opportunity_slot > ?
			  AND claimed_by IS NOT NULL
			""";

	private static final RowMapper<DeliveryOpportunity> OPPORTUNITY_ROW_MAPPER =
			JdbcDeliveryOpportunityRepository::mapOpportunity;

	private static final RowMapper<DeliveryOpportunitySlot> SLOT_ROW_MAPPER =
			JdbcDeliveryOpportunityRepository::mapSlot;

	private final JdbcTemplate jdbcTemplate;

	public JdbcDeliveryOpportunityRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public DeliveryOpportunity create(DeliveryOpportunity opportunity) {
		jdbcTemplate.update(
				INSERT_OPPORTUNITY,
				opportunity.id(),
				opportunity.regionId(),
				opportunity.zoneId(),
				toTimestamp(opportunity.bookingWindowStart()),
				toTimestamp(opportunity.bookingWindowEnd()),
				opportunity.capacity(),
				toTimestamp(opportunity.createdAt()));
		return opportunity;
	}

	@Override
	public Optional<DeliveryOpportunity> findById(UUID id) {
		List<DeliveryOpportunity> results =
				jdbcTemplate.query(SELECT_OPPORTUNITY_BY_ID, OPPORTUNITY_ROW_MAPPER, id);
		return results.stream().findFirst();
	}

	@Override
	public List<DeliveryOpportunity> list() {
		return jdbcTemplate.query(SELECT_ALL_OPPORTUNITIES, OPPORTUNITY_ROW_MAPPER);
	}

	@Override
	public DeliveryOpportunity update(DeliveryOpportunity opportunity) {
		int updated = jdbcTemplate.update(
				UPDATE_OPPORTUNITY,
				opportunity.regionId(),
				opportunity.zoneId(),
				toTimestamp(opportunity.bookingWindowStart()),
				toTimestamp(opportunity.bookingWindowEnd()),
				opportunity.capacity(),
				opportunity.id());
		if (updated == 0) {
			throw new IllegalStateException("Delivery opportunity not found: " + opportunity.id());
		}
		return opportunity;
	}

	@Override
	public void delete(UUID id) {
		throw new UnsupportedOperationException("Delivery opportunities cannot be deleted");
	}

	@Override
	public DeliveryOpportunitySlot createSlot(DeliveryOpportunitySlot slot) {
		jdbcTemplate.update(
				INSERT_SLOT,
				slot.id(),
				slot.opportunityId(),
				slot.opportunitySlot(),
				slot.claimedBy(),
				toTimestamp(slot.claimedAt()));
		return findSlot(slot.id())
				.orElseThrow(() -> new IllegalStateException("Failed to load slot after insert: " + slot.id()));
	}

	@Override
	public Optional<DeliveryOpportunitySlot> findSlot(UUID slotId) {
		List<DeliveryOpportunitySlot> results = jdbcTemplate.query(
				"""
				SELECT id, opportunity_id, opportunity_slot, claimed_by, claimed_at, version
				FROM delivery_opportunity_slots
				WHERE id = ?
				""",
				SLOT_ROW_MAPPER,
				slotId);
		return results.stream().findFirst();
	}

	@Override
	public Optional<DeliveryOpportunitySlot> findSlotByOpportunityIdAndSlot(
			UUID opportunityId, int opportunitySlot) {
		List<DeliveryOpportunitySlot> results = jdbcTemplate.query(
				SELECT_SLOT_BY_OPPORTUNITY_AND_SLOT,
				SLOT_ROW_MAPPER,
				opportunityId,
				opportunitySlot);
		return results.stream().findFirst();
	}

	@Override
	public Optional<DeliveryOpportunitySlot> claimSlotIfUnclaimed(
			UUID opportunityId, int opportunitySlot, UUID driverId, Instant claimedAt) {
		return jdbcTemplate
				.query(
						CLAIM_SLOT_IF_UNCLAIMED,
						SLOT_ROW_MAPPER,
						driverId,
						toTimestamp(claimedAt),
						opportunityId,
						opportunitySlot)
				.stream()
				.findFirst();
	}

	@Override
	public List<DeliveryOpportunitySlot> listSlotsByOpportunityId(UUID opportunityId) {
		return jdbcTemplate.query(SELECT_SLOTS_BY_OPPORTUNITY, SLOT_ROW_MAPPER, opportunityId);
	}

	@Override
	public DeliveryOpportunitySlot updateSlot(DeliveryOpportunitySlot slot) {
		int updated = jdbcTemplate.update(
				UPDATE_SLOT,
				slot.claimedBy(),
				toTimestamp(slot.claimedAt()),
				slot.id());
		if (updated == 0) {
			throw new IllegalStateException("Delivery opportunity slot not found: " + slot.id());
		}
		return findSlot(slot.id())
				.orElseThrow(() -> new IllegalStateException("Failed to load slot after update: " + slot.id()));
	}

	@Override
	public void deleteSlot(UUID slotId) {
		jdbcTemplate.update("DELETE FROM delivery_opportunity_slots WHERE id = ?", slotId);
	}

	@Override
	public void deleteUnclaimedSlotsAbove(UUID opportunityId, int maxSlotNumber) {
		jdbcTemplate.update(DELETE_UNCLAIMED_SLOTS_ABOVE, opportunityId, maxSlotNumber);
	}

	@Override
	public int countClaimedSlotsAbove(UUID opportunityId, int maxSlotNumber) {
		Integer count = jdbcTemplate.queryForObject(
				COUNT_CLAIMED_SLOTS_ABOVE, Integer.class, opportunityId, maxSlotNumber);
		return count != null ? count : 0;
	}

	private static DeliveryOpportunity mapOpportunity(ResultSet rs, int rowNum) throws SQLException {
		return new DeliveryOpportunity(
				rs.getObject("id", UUID.class),
				rs.getInt("region_id"),
				rs.getInt("zone_id"),
				toInstant(rs.getTimestamp("booking_window_start")),
				toInstant(rs.getTimestamp("booking_window_end")),
				rs.getInt("capacity"),
				toInstant(rs.getTimestamp("created_at")));
	}

	private static DeliveryOpportunitySlot mapSlot(ResultSet rs, int rowNum) throws SQLException {
		return new DeliveryOpportunitySlot(
				rs.getObject("id", UUID.class),
				rs.getObject("opportunity_id", UUID.class),
				rs.getInt("opportunity_slot"),
				rs.getObject("claimed_by", UUID.class),
				toInstant(rs.getTimestamp("claimed_at")),
				rs.getInt("version"));
	}

	private static Timestamp toTimestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private static Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}
}

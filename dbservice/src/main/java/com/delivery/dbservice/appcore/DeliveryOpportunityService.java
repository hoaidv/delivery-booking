package com.delivery.dbservice.appcore;

import com.delivery.dbcommon.models.DeliveryOpportunity;
import com.delivery.dbcommon.models.DeliveryOpportunitySlot;
import com.delivery.dbcommon.repository.DeliveryOpportunityRepository;
import com.delivery.dbservice.dtos.CreateDeliveryOpportunityReq;
import com.delivery.dbservice.dtos.DeliveryOpportunityDto;
import com.delivery.dbservice.dtos.UpdateDeliveryOpportunityReq;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@link DeliveryOpportunity}'s service has basic functions for manipulating 
 * delivery opportunities and their slots.
 * <br><br>
 * It is here only to support testing {@link BookingService} and dbworker performance
 * <br><br>
 */
@Service
public class DeliveryOpportunityService {

	private static final Duration UPDATE_DEADLINE_BEFORE_WINDOW = Duration.ofHours(1);

	private final DeliveryOpportunityRepository opportunityRepository;

	public DeliveryOpportunityService(DeliveryOpportunityRepository opportunityRepository) {
		this.opportunityRepository = opportunityRepository;
	}

	@Transactional
	public DeliveryOpportunityDto create(CreateDeliveryOpportunityReq request) {
		DeliveryOpportunityValidator.validateCreate(request);

		UUID id = UUID.randomUUID();
		Instant createdAt = Instant.now();
		DeliveryOpportunity opportunity = new DeliveryOpportunity(
				id,
				request.regionId(),
				request.zoneId(),
				request.bookingWindowStart(),
				request.bookingWindowEnd(),
				request.capacity(),
				createdAt);

		opportunityRepository.create(opportunity);
		reserveSlots(opportunity);
		return DeliveryOpportunityDto.from(opportunity);
	}

	public Optional<DeliveryOpportunityDto> findById(UUID id) {
		return opportunityRepository.findById(id).map(DeliveryOpportunityDto::from);
	}

	public List<DeliveryOpportunityDto> list() {
		return opportunityRepository.list().stream().map(DeliveryOpportunityDto::from).toList();
	}

  /**
	 * Updating a delivery opportunity here may cause "race condition" with dbworker.
	 * As dbworker is incharge of processing claims, updating the delivery opportunity's capacity
	 * and opportunity slots's claimed_by, claimed_at.
	 * <br><br>
	 * Again, this is here only to support testing {@link BookingService} and dbworker performance.
	 */
	@Transactional
	public DeliveryOpportunityDto update(UUID id, UpdateDeliveryOpportunityReq request) {
		DeliveryOpportunityValidator.validateUpdate(request);

		DeliveryOpportunity existing = opportunityRepository
				.findById(id)
				.orElseThrow(() -> new DeliveryOpportunityNotFoundException(id));

		assertUpdateAllowed(existing.bookingWindowStart());

		if (request.capacity() < existing.capacity()) {
			int claimedAbove = opportunityRepository.countClaimedSlotsAbove(id, request.capacity());
			if (claimedAbove > 0) {
				throw new DeliveryOpportunityValidationException(
						"cannot reduce capacity: claimed slots exist above new capacity");
			}
			opportunityRepository.deleteUnclaimedSlotsAbove(id, request.capacity());
		}

		DeliveryOpportunity updated = new DeliveryOpportunity(
				existing.id(),
				request.regionId(),
				request.zoneId(),
				request.bookingWindowStart(),
				request.bookingWindowEnd(),
				request.capacity(),
				existing.createdAt());

		opportunityRepository.update(updated);

		if (request.capacity() > existing.capacity()) {
			addSlots(updated, existing.capacity() + 1, request.capacity());
		}

		return DeliveryOpportunityDto.from(updated);
	}

	private void reserveSlots(DeliveryOpportunity opportunity) {
		addSlots(opportunity, 1, opportunity.capacity());
	}

	private void addSlots(DeliveryOpportunity opportunity, int fromSlot, int toSlot) {
		for (int slotNumber = fromSlot; slotNumber <= toSlot; slotNumber++) {
			DeliveryOpportunitySlot slot = new DeliveryOpportunitySlot(
					UUID.randomUUID(),
					opportunity.id(),
					slotNumber,
					null,
					null,
					0);
			opportunityRepository.createSlot(slot);
		}
	}

	private void assertUpdateAllowed(Instant bookingWindowStart) {
		Instant deadline = bookingWindowStart.minus(UPDATE_DEADLINE_BEFORE_WINDOW);
		if (!Instant.now().isBefore(deadline)) {
			throw new DeliveryOpportunityUpdateForbiddenException(
					"updates are only allowed until 1 hour before booking window start");
		}
	}
}

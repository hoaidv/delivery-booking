package com.delivery.dbservice.present;

import com.delivery.dbservice.appcore.BookingService;
import com.delivery.dbservice.dtos.DeliveryOpportunityClaimDto;
import com.delivery.dbservice.dtos.DeliveryOpportunityClaimRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers/{driverId}/claims")
public class BookingServiceController {

	private final BookingService bookingService;

	public BookingServiceController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@PostMapping
	public ResponseEntity<DeliveryOpportunityClaimDto> claim(
			@PathVariable UUID driverId,
			@RequestBody DeliveryOpportunityClaimRequest body) {
		DeliveryOpportunityClaimRequest request = new DeliveryOpportunityClaimRequest(
				driverId, body.opportunityId(), body.metadata());
		DeliveryOpportunityClaimDto created = bookingService.processClaim(request);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(created);
	}

	@GetMapping
	public ResponseEntity<List<DeliveryOpportunityClaimDto>> listClaims(
			@PathVariable UUID driverId,
			@RequestParam(required = false) UUID beforeRequestedAt,
			@RequestParam(defaultValue = "" + BookingService.DEFAULT_CLAIM_PAGE_SIZE) int limit) {
		List<DeliveryOpportunityClaimDto> claims = bookingService.listClaims(
				driverId, Optional.ofNullable(beforeRequestedAt), limit);
		return ResponseEntity.ok(claims);
	}

	@GetMapping(params = "opportunityId")
	public ResponseEntity<DeliveryOpportunityClaimDto> getClaim(
			@PathVariable UUID driverId,
			@RequestParam UUID opportunityId) {
		return bookingService
				.getClaim(driverId, opportunityId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}

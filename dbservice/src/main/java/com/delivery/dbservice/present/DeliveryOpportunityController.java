package com.delivery.dbservice.present;

import com.delivery.dbservice.appcore.DeliveryOpportunityService;
import com.delivery.dbservice.dtos.CreateDeliveryOpportunityReq;
import com.delivery.dbservice.dtos.DeliveryOpportunityDto;
import com.delivery.dbservice.dtos.UpdateDeliveryOpportunityReq;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@link DeliveryOpportunityController} has basic functions for manipulating 
 * delivery opportunities.
 * <br><br>
 * It is here only to support testing dbservice and dbworker performance
 * <br><br>
 */
@RestController
@RequestMapping("/api/v1/delivery-opportunities")
public class DeliveryOpportunityController {

	private final DeliveryOpportunityService deliveryOpportunityService;

	public DeliveryOpportunityController(DeliveryOpportunityService deliveryOpportunityService) {
		this.deliveryOpportunityService = deliveryOpportunityService;
	}

	@PostMapping
	public ResponseEntity<DeliveryOpportunityDto> create(@RequestBody CreateDeliveryOpportunityReq request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(deliveryOpportunityService.create(request));
	}

	@GetMapping("/{id}")
	public ResponseEntity<DeliveryOpportunityDto> getById(@PathVariable UUID id) {
		return deliveryOpportunityService.findById(id)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping
	public ResponseEntity<List<DeliveryOpportunityDto>> list() {
		return ResponseEntity.ok(deliveryOpportunityService.list());
	}

	@PutMapping("/{id}")
	public ResponseEntity<DeliveryOpportunityDto> update(
			@PathVariable UUID id, @RequestBody UpdateDeliveryOpportunityReq request) {
		return ResponseEntity.ok(deliveryOpportunityService.update(id, request));
	}
}

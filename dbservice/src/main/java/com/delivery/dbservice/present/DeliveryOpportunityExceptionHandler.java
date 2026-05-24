package com.delivery.dbservice.present;

import com.delivery.dbservice.appcore.BookingWindowClosedException;
import com.delivery.dbservice.appcore.DeliveryOpportunityNotFoundException;
import com.delivery.dbservice.appcore.DeliveryOpportunitySlotUnavailableException;
import com.delivery.dbservice.appcore.DeliveryOpportunityUpdateForbiddenException;
import com.delivery.dbservice.appcore.DeliveryOpportunityValidationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DeliveryOpportunityExceptionHandler {

	@ExceptionHandler(DeliveryOpportunityValidationException.class)
	public ResponseEntity<Map<String, String>> handleValidation(DeliveryOpportunityValidationException ex) {
		return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(DeliveryOpportunityNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(DeliveryOpportunityNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(DeliveryOpportunityUpdateForbiddenException.class)
	public ResponseEntity<Map<String, String>> handleUpdateForbidden(
			DeliveryOpportunityUpdateForbiddenException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(BookingWindowClosedException.class)
	public ResponseEntity<Map<String, String>> handleBookingWindowClosed(BookingWindowClosedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(DeliveryOpportunitySlotUnavailableException.class)
	public ResponseEntity<Map<String, String>> handleSlotUnavailable(
			DeliveryOpportunitySlotUnavailableException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
	}
}

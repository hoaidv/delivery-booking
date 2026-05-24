package com.delivery.dbservice.appcore;

import java.util.UUID;

public class BookingWindowClosedException extends RuntimeException {

	public BookingWindowClosedException(UUID opportunityId) {
		super("booking window is not open for opportunity: " + opportunityId);
	}
}

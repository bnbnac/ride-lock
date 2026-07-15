package com.bnbnac.ride_lock.driver;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record LocationUpdateRequest(
		@DecimalMin("-180.0") @DecimalMax("180.0") double lng,
		@DecimalMin("-90.0") @DecimalMax("90.0") double lat) {
}

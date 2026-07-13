package com.bnbnac.ride_lock.matching;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class NoAvailableDriverException extends RuntimeException {

	public NoAvailableDriverException() {
		super("no available driver nearby");
	}
}

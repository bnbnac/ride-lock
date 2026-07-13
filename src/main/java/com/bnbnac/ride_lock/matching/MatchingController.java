package com.bnbnac.ride_lock.matching;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matchings")
public class MatchingController {

	private final MatchingService matchingService;

	public MatchingController(MatchingService matchingService) {
		this.matchingService = matchingService;
	}

	@PostMapping
	public MatchingResult match(@RequestBody MatchingRequest request) {
		return matchingService.match(request.lng(), request.lat());
	}

}

package com.bnbnac.ride_lock.driver;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

// LocationUpdateService.reportLocation()의 호출 순서(status 먼저, location 나중)만 검증하는
// 순수 단위 테스트 - 결과(DB에 실제로 뭐가 남았는지)는 LocationUpdateServiceTest(통합 테스트)가
// 맡는다. FK가 없어서 순서가 틀려도 지금은 DB 레벨에서 안 터지므로, 이 불변식은 DB 제약이 아니라
// 이 테스트로만 지켜진다.
class LocationUpdateServiceOrderTest {

	@Test
	void insertsIdleStatusBeforeUpsertingLocation() {
		DriverLocationRepository driverLocationRepository = mock(DriverLocationRepository.class);
		DriverStatusRepository driverStatusRepository = mock(DriverStatusRepository.class);
		LocationUpdateService service = new LocationUpdateService(driverLocationRepository, driverStatusRepository);

		service.reportLocation(1L, 126.9707, 37.5547);

		InOrder inOrder = inOrder(driverStatusRepository, driverLocationRepository);
		inOrder.verify(driverStatusRepository).insertIdleIfAbsent(1L);
		inOrder.verify(driverLocationRepository).upsertLocation(1L, 126.9707, 37.5547);
	}

}

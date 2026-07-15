package com.bnbnac.ride_lock.driver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriverLocationController.class)
class DriverLocationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LocationUpdateService locationUpdateService;

	@Test
	void rejectsOutOfRangeLongitudeWithBadRequest() throws Exception {
		mockMvc.perform(put("/drivers/1/location")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lng\":200,\"lat\":37.55}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(locationUpdateService);
	}

	@Test
	void rejectsOutOfRangeLatitudeWithBadRequest() throws Exception {
		mockMvc.perform(put("/drivers/1/location")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lng\":126.97,\"lat\":-91}"))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(locationUpdateService);
	}

	@Test
	void acceptsValidCoordinatesAndDelegatesToService() throws Exception {
		mockMvc.perform(put("/drivers/1/location")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lng\":126.97,\"lat\":37.55}"))
				.andExpect(status().isNoContent());

		verify(locationUpdateService).reportLocation(1L, 126.97, 37.55);
	}

	// @DecimalMax/@DecimalMin은 기본이 inclusive라 경계값 자체는 통과해야 한다 - 그 사실을
	// 실제로 확인해두지 않으면 "범위 검증 있음"이라는 주장이 명확히 밖(200/-91)에만 기대는 게 된다.
	@Test
	void acceptsMaximumBoundaryCoordinates() throws Exception {
		mockMvc.perform(put("/drivers/1/location")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lng\":180.0,\"lat\":90.0}"))
				.andExpect(status().isNoContent());

		verify(locationUpdateService).reportLocation(1L, 180.0, 90.0);
	}

	@Test
	void acceptsMinimumBoundaryCoordinates() throws Exception {
		mockMvc.perform(put("/drivers/1/location")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lng\":-180.0,\"lat\":-90.0}"))
				.andExpect(status().isNoContent());

		verify(locationUpdateService).reportLocation(1L, -180.0, -90.0);
	}

}

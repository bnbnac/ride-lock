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

}

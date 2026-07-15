package com.bnbnac.ride_lock.driver;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

// WGS84 형식 검증만 한다(지구 전체 범위) - "한국/서울 서비스 지역 안인가"는 별개의 도메인 규칙이라
// 여기(DTO)에 넣지 않는다. 시뮬레이터 데모 좌표에 검증을 맞추면 커버리지가 바뀔 때마다 고쳐야 한다.
public record LocationUpdateRequest(
		@DecimalMin("-180.0") @DecimalMax("180.0") double lng,
		@DecimalMin("-90.0") @DecimalMax("90.0") double lat) {
}

// 기사 위치 랜덤워크 시뮬레이터. 인앱 스케줄러 대신 k6를 재사용한다 - "N명이 각자 주기적으로
// 요청을 날린다"는 구조가 3주차 부하테스트 스크립트와 동일해서 별도 Java 프로세스가 불필요했다.
//
// 실행: k6 run loadtest/driver-simulator.js
// 옵션(env var): BASE_URL(기본 http://localhost:8080), DRIVER_COUNT(=vus, 기본 20),
//                DURATION(기본 10m), INTERVAL_SECONDS(기본 5) - §7 위치 갱신 주기 실험과 그대로 연결됨
import http from 'k6/http';
import { sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const INTERVAL_SECONDS = Number(__ENV.INTERVAL_SECONDS) || 5;

const ORIGIN_LNG = 126.9707;
const ORIGIN_LAT = 37.5547;
const BOUND_DEGREES = 0.03; // 서울역 중심 반경 약 3km - 매칭 반경(5km) 밖으로 못 나가게 clamp
const STEP_DEGREES = 0.0004;

export const options = {
	vus: Number(__ENV.DRIVER_COUNT) || 20,
	duration: __ENV.DURATION || '10m',
};

// VU 초기화 시 한 번만 실행되고, 이후 반복(iteration) 사이에는 이 값이 그대로 유지된다.
let lng = ORIGIN_LNG + (Math.random() - 0.5) * BOUND_DEGREES;
let lat = ORIGIN_LAT + (Math.random() - 0.5) * BOUND_DEGREES;

function clamp(value, center, half) {
	return Math.min(center + half, Math.max(center - half, value));
}

export default function () {
	lng = clamp(lng + (Math.random() - 0.5) * STEP_DEGREES, ORIGIN_LNG, BOUND_DEGREES);
	lat = clamp(lat + (Math.random() - 0.5) * STEP_DEGREES, ORIGIN_LAT, BOUND_DEGREES);

	http.put(`${BASE_URL}/drivers/${__VU}/location`, JSON.stringify({ lng, lat }), {
		headers: { 'Content-Type': 'application/json' },
	});

	sleep(INTERVAL_SECONDS);
}

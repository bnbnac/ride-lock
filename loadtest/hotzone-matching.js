// loadtest/hotzone-matching.js
// 승객 100명이 고정 기사 5명(driver_id 9001~9005, loadtest/seed-hotzone.sql로 시드)을 놓고
// 지속적으로 경쟁하는 핫존 시나리오. think-time 없음 - 응답 받는 즉시 재요청(closed workload).
// 원점 좌표는 고정(지터 없음) - 매 반복이 정확히 같은 5명을 후보로 삼아야 "핫존" 경합 구조가
// 흐려지지 않는다. 성공(200)/컨텐션 실패(409)/그 외 에러를 분리 집계 - 409는 락 경합에 의한
// 정상 실패라 "버그성 에러"와 같은 지표로 섞으면 오독을 유발한다.
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ORIGIN_LNG = 126.9707;
const ORIGIN_LAT = 37.5547;

export const options = {
	vus: 100,
	duration: __ENV.DURATION || '3m',
};

const successCount = new Counter('match_success');
const conflictCount = new Counter('match_conflict_409');
const errorCount = new Counter('match_unexpected_error');
const successDuration = new Trend('match_success_duration');

export default function () {
	const res = http.post(
		`${BASE_URL}/matchings`,
		JSON.stringify({ lng: ORIGIN_LNG, lat: ORIGIN_LAT }),
		{ headers: { 'Content-Type': 'application/json' } },
	);

	if (res.status === 200) {
		successCount.add(1);
		successDuration.add(res.timings.duration);
	} else if (res.status === 409) {
		conflictCount.add(1);
	} else {
		errorCount.add(1);
	}
}

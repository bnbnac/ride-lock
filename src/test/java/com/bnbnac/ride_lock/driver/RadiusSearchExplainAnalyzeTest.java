package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// GiST 인덱스 유무에 따른 반경 검색 실행계획 차이 실측 (§3.4). 트랜잭션 롤백으로 더미 데이터/인덱스 DROP 정리.
// 앱 정합성을 지키는 회귀 테스트가 아니라 PostgreSQL 플래너 행동에 대한 가정을 실측으로 검증하는
// 실험이라, 기본 test 태스크에서 제외하고 별도 explainAnalyze 태스크로만 돌린다.
@Tag("planner-experiment")
@SpringBootTest
@Transactional
class RadiusSearchExplainAnalyzeTest extends AbstractIntegrationTest {

	private static final int DRIVER_COUNT = 50_000;

	private static final String SPATIAL_INDEX = "idx_driver_location_gist";

	private static final String RADIUS_ONLY_QUERY = """
			EXPLAIN ANALYZE
			SELECT driver_id
			FROM driver_location
			WHERE ST_DWithin(location, ST_Transform(ST_SetSRID(ST_MakePoint(126.9707, 37.5547), 4326), 5179), 5000)
			""";

	private static final String JOIN_QUERY = """
			EXPLAIN ANALYZE
			SELECT l.driver_id,
			       ST_Distance(l.location, ST_Transform(ST_SetSRID(ST_MakePoint(126.9707, 37.5547), 4326), 5179)) AS dist_m
			FROM driver_location l
			JOIN driver_status s ON s.driver_id = l.driver_id
			WHERE s.status = 'IDLE'
			  AND ST_DWithin(l.location,
			                 ST_Transform(ST_SetSRID(ST_MakePoint(126.9707, 37.5547), 4326), 5179),
			                 5000)
			ORDER BY dist_m
			LIMIT 20
			""";

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void gistIndexChangesRadiusScanFromSeqScanToIndexScan() {
		seedDrivers();

		String withIndex = explain(RADIUS_ONLY_QUERY);
		System.out.println("=== driver_location 반경 검색, GiST 인덱스 있음 ===\n" + withIndex);
		assertThat(withIndex).contains(SPATIAL_INDEX);

		// §3.4 옵티마이저 함정 가설(인덱스가 있어도 조인이 회피할 수 있다)은 인덱스가
		// 살아있는 시점에 직접 실행해야 검증할 수 있다. 실측 결과: 반증됨 — GiST 인덱스를
		// 그대로 사용한다(Bitmap Index Scan on idx_driver_location_gist).
		String joinWithIndex = explain(JOIN_QUERY);
		System.out.println("=== 조인 쿼리(status+반경), GiST 인덱스 있음 ===\n" + joinWithIndex);
		assertThat(joinWithIndex).contains(SPATIAL_INDEX);

		dropSpatialIndex();

		String withoutIndex = explain(RADIUS_ONLY_QUERY);
		System.out.println("=== driver_location 반경 검색, GiST 인덱스 없음 ===\n" + withoutIndex);
		assertThat(withoutIndex).contains("Seq Scan on driver_location");

		// 조인 쿼리는 GiST 인덱스가 없어도 Seq Scan으로 떨어지지 않는다. driver_status.status
		// 필터(IDLE 약 50%)의 선택도가 충분히 높아, 옵티마이저가 driver_status를 먼저 필터링한 뒤
		// driver_location을 PK(driver_location_pkey)로 Nested Loop 조인하는 경로로 자연스럽게
		// 우회하기 때문이다 — 진짜 "함정"은 인덱스 회피가 아니라 이 대안 경로의 존재였다.
		String joinWithoutIndex = explain(JOIN_QUERY);
		System.out.println("=== 조인 쿼리(status+반경), GiST 인덱스 없음 ===\n" + joinWithoutIndex);
		assertThat(joinWithoutIndex).contains("driver_location_pkey");
	}

	private void seedDrivers() {
		entityManager.createNativeQuery(
				"INSERT INTO driver_status (driver_id, status, version, updated_at) " +
						"SELECT gs, CASE WHEN random() < 0.5 THEN 'IDLE' ELSE 'ON_TRIP' END, 0, now() " +
						"FROM generate_series(1, " + DRIVER_COUNT + ") AS gs")
				.executeUpdate();

		entityManager.createNativeQuery(
				"INSERT INTO driver_location (driver_id, location, updated_at) " +
						"SELECT gs, ST_Transform(ST_SetSRID(ST_MakePoint(" +
						"126.9707 + (random() - 0.5) * 0.5, " +
						"37.5547 + (random() - 0.5) * 0.5" +
						"), 4326), 5179), now() " +
						"FROM generate_series(1, " + DRIVER_COUNT + ") AS gs")
				.executeUpdate();
	}

	private void dropSpatialIndex() {
		entityManager.createNativeQuery("DROP INDEX " + SPATIAL_INDEX).executeUpdate();
	}

	@SuppressWarnings("unchecked")
	private String explain(String sql) {
		List<Object> lines = entityManager.createNativeQuery(sql).getResultList();
		StringBuilder plan = new StringBuilder();
		for (Object line : lines) {
			plan.append(line).append('\n');
		}
		return plan.toString();
	}

}

package com.bnbnac.ride_lock.driver;

import com.bnbnac.ride_lock.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// GiST 인덱스 유무에 따른 반경 검색 실행계획 차이 실측 (§3.4). 트랜잭션 롤백으로 더미 데이터/인덱스 DROP 정리.
@SpringBootTest
@Transactional
class RadiusSearchExplainAnalyzeTest extends AbstractIntegrationTest {

	private static final int DRIVER_COUNT = 50_000;

	private static final String RADIUS_ONLY_QUERY = """
			EXPLAIN ANALYZE
			SELECT driver_id
			FROM driver_location
			WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(126.9707, 37.5547), 4326)::geography, 5000)
			""";

	private static final String JOIN_QUERY = """
			EXPLAIN ANALYZE
			SELECT l.driver_id,
			       ST_Distance(l.location, ST_SetSRID(ST_MakePoint(126.9707, 37.5547), 4326)::geography) AS dist_m
			FROM driver_location l
			JOIN driver_status s ON s.driver_id = l.driver_id
			WHERE s.status = 'IDLE'
			  AND ST_DWithin(l.location,
			                 ST_SetSRID(ST_MakePoint(126.9707, 37.5547), 4326)::geography,
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
		assertThat(withIndex).contains("Index Scan");

		dropSpatialIndex();

		String withoutIndex = explain(RADIUS_ONLY_QUERY);
		System.out.println("=== driver_location 반경 검색, GiST 인덱스 없음 ===\n" + withoutIndex);
		assertThat(withoutIndex).contains("Seq Scan");

		// status 필터(§3.4 옵티마이저 함정) 포함 조인 쿼리는 플랜이 통계에 따라 달라질 수 있어
		// 특정 노드를 강제로 assert하지 않고 실측 결과만 남긴다.
		String joinPlanWithoutSpatialIndex = explain(JOIN_QUERY);
		System.out.println("=== 조인 쿼리(status+반경), GiST 인덱스 없음 ===\n" + joinPlanWithoutSpatialIndex);
	}

	private void seedDrivers() {
		entityManager.createNativeQuery(
				"INSERT INTO driver_status (driver_id, status, version, updated_at) " +
						"SELECT gs, CASE WHEN random() < 0.5 THEN 'IDLE' ELSE 'ON_TRIP' END, 0, now() " +
						"FROM generate_series(1, " + DRIVER_COUNT + ") AS gs")
				.executeUpdate();

		entityManager.createNativeQuery(
				"INSERT INTO driver_location (driver_id, location, updated_at) " +
						"SELECT gs, ST_SetSRID(ST_MakePoint(" +
						"126.9707 + (random() - 0.5) * 0.5, " +
						"37.5547 + (random() - 0.5) * 0.5" +
						"), 4326)::geography, now() " +
						"FROM generate_series(1, " + DRIVER_COUNT + ") AS gs")
				.executeUpdate();
	}

	private void dropSpatialIndex() {
		entityManager.createNativeQuery("DROP INDEX idx_driver_location_gist").executeUpdate();
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

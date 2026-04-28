# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Spring Boot 4 + Kotlin (JVM 25) 기반 LBS 서비스. 가격·우회 비용을 함께 고려해 주유소를 추천한다. 두 가지 모드:

- **반경 기반** (`GasStationRadiusRecommender`): OPINET `aroundAll` 1회 호출로 후보 수집 후 점수 정렬.
- **경로 기반** (`GasStationRouteRecommender`): Kakao Mobility 기본 경로 → DB 후보 수집 (polyline MBR cascade) → price lower-bound pruning + Kakao 경유 경로 재호출 → 실측 우회 기반 점수 정렬. **현재 집중 개발 영역**.

좌표 체계: 내부/응답은 WGS84, OPINET은 KATEC(EPSG:5181). `Coordinate`가 두 표현을 지연 변환으로 캡슐화한다.

## 공통 명령

```bash
./gradlew build                                   # 컴파일 + 전체 테스트
./gradlew test                                    # 테스트만
./gradlew test --tests "*RouteGasStationE2eTest*" # 단일 테스트 클래스
./gradlew test --tests "*RouteRecommenderTest.스코어*"  # 단일 메서드 (백틱 테스트명도 glob 가능)
./gradlew bootRun                                 # 로컬 구동
./gradlew runOpinetLocalTest                      # OPINET CSV 다운로드 수동 테스트 (자격증명 하드코딩 로컬 전용)
./gradlew runOpinetTest                           # OPINET CSV 다운로드 (환경변수 필요)
```

테스트는 **Testcontainers MySQL 8**로 돈다(`TestcontainersConfiguration`). Docker 데몬 필요. E2E는 `@SpringBootTest` + `@MockitoBean KakaoDirectionsClient`로 Kakao 호출만 스텁한다.

환경변수는 `.env.local`·`.env.prod`에 있고 SessionStart 훅이 로드한다(`.claude/hooks/session-start.sh`).

## 아키텍처 핵심

### 경로 기반 추천 파이프라인 (`GasStationRouteRecommender`)

설계 문서: `docs/route-recommendation-design.md`, `docs/route-recommendation-improvements.md`, `docs/recommendation-flow.md`.

1. **baseRoute** — Kakao Directions로 출발→도착 polyline + 총 거리·시간 수신.
2. **후보 수집 cascade** (`gatherCandidates`):
   - `POLYLINE_MBR`: `BoundingBox.aroundPolyline(polyline, bufferMeters)`로 DB 조회. buffer는 `maxDetour × 2`로 동적 — `maxDetour = min(baseDistance × 0.3, 10km)`.
   - 가격 결합 + 가격 없는 것 drop + 가격 오름차순 정렬.
   - `ROUTE_PRICE_CEILING`: 경로상 후보(`GeoUtils.calculateMinDistanceToPolyline ≤ ON_ROUTE_RADIUS_METERS(500m)`) 최저가 `p_route`를 cap으로 가격 ≤ `p_route` 후보만 보존. 후보 수에 무관하게 항상 시도. 경로상 후보 0개면 fallback(무변경).
   - `PRICE_CAPPED`: `HARD_CAP(30)` 초과 시 저가 상위 30개만 (외부 호출 강제 상한).
3. **rankByPriceLowerBound**: 가격 오름차순 탐색 + Kakao 경유 경로 재호출. `price × liters > kthBestScore`면 **조기 종료** (수학적 하한 보장). `actualDetour > maxDetour`는 이상치로 배제.
4. 스코어 = `가격 × 주유량 + (우회km/연비) × 가격 + (우회초/3600) × 최저시급(10320)`. **수정 금지** — 변경 시 설계 문서 근거(Kelley & Kuby 2013) 업데이트 동반.

반환은 `RecommendResult(scored, maxPriceInCandidates)`. 컨트롤러가 `GasStationResponse.fromList`로 `estimatedSavings` 등 파생 필드를 붙인다.

### `ScoredGasStation` 원칙

저장 필드는 **계산 불가능한 입력만**(price, detourDistanceMeters, detourSeconds, refuelLiters, fuelEfficiency, isActualDetour). `detourKm / fuelCost / detourFuelCost / detourTimeCost / score`는 모두 `val get()` 계산 프로퍼티. `of()` 팩토리는 제거했으니 생성자 직접 호출.

### 모듈 구조

- `gasstation/controller` — REST 엔드포인트 `/api/gas-stations/recommendations/{radius,route}`.
- `gasstation/service` — 추천 서비스 2개.
- `gasstation/client` — 외부 API: `OpinetClient`(반경), `KakaoDirectionsClient`(경로·경유).
- `gasstation/batch` — OPINET CSV 다운로드·임포트. `OpinetCsvScheduler`가 정기 실행.
- `gasstation/repository` — JPA. `GasStationRepository.findInBounds(BoundingBox)`는 SpEL로 BoundingBox 필드를 푼다.
- `geo` — `Coordinate`(WGS84↔KATEC lazy), `BoundingBox`(around/aroundPolyline), `GeoUtils`(polyline 거리), `CoordinateConverter`(proj4j EPSG).
- `infra` — CORS, 글로벌 예외 핸들러, Kakao/OPINET 설정 프로퍼티, `RestClientConfig`.

### DB / 마이그레이션

Flyway: `src/main/resources/db/migration/V*__*.sql`. `V4__add_gas_station_geo_index.sql`이 위경도 복합 인덱스 추가 — `findInBounds` 경로 성능의 핵심. JPA `ddl-auto=validate`이므로 스키마 변경은 반드시 Flyway 마이그레이션으로.

## 프로젝트 규약

- **커밋 prefix 강제** (`.claude/hooks/validate-commit.sh`): `feat: / fix: / refactor: / docs: / test: / chore: / style: / perf:` 중 하나. 위반 시 훅이 차단한다.
- **Kotlin 메서드명 동사형** (`.claude/hooks/check-method-naming.sh`): 팩토리 관용어(`of/from/around/aroundPolyline/invoke`)만 예외. 위반은 경고.
- **Protected files**: `.claude/hooks/protect-files.sh`가 Edit/Write 시 민감 파일을 막는다. 차단되면 파일 자체를 수정하지 말 것.
- **컨벤션**: 한국어 로그/주석, OOP 스타일(불필요한 확장함수·최상위 유틸 지양), JPA 엔티티는 allOpen 플러그인으로 open.
- **테스트**: 모든 테스트는 Testcontainers MySQL에서 동작해야 하며, Kakao/OPINET은 `@MockitoBean`으로 스텁.

## Out of Scope — 건드리지 말 것 (현 브랜치 기준)

- 스코어 공식 구조 변경(비선형 페널티, 골든존 등) — 문헌 근거 불충분.
- PRICE/ROUTE/BALANCED 같은 사용자 모드 분리.
- price lower-bound pruning 로직 변경.
- `/recommendations/route` 요청 파라미터 breaking change (응답 필드 추가는 허용).

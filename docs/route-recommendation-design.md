# 경로 기반 주유소 추천 개선 설계

> ⚠️ **구버전 설계 기록**: 이 문서는 `TIGHT_CORRIDOR`(직선 거리 ≤ 2 km, `N_THRESHOLD = 30` 트리거) 기반 cascade를 가정합니다. 현재 구현은 식 (2) 가격 하한에 직접 연결되는 `ROUTE_PRICE_CEILING`(경로상 후보 최저가 cap, N에 무관하게 항상 적용)으로 교체되었으며, 본 문서의 `TIGHT_CORRIDOR_METERS`·`N_THRESHOLD` 상수는 더 이상 코드에 존재하지 않습니다. 현재 cascade의 권위 있는 명세는 `CLAUDE.md`와 `docs/paper/route-focus-submission.md` Ⅲ.4 표 2를 참고하세요. 이 문서는 옛 설계 결정 과정의 기록으로 보존됩니다.

본 문서는 [route-recommendation-improvements.md](./route-recommendation-improvements.md)의 2개 개선책에 대한 구체 설계다.

## TL;DR

- **핵심 변경**: 후보 수집을 **polyline MBR-first adaptive cascade**로 전환. 기존 `BoundingBox.aroundPolyline` 유틸 재사용, 신규 지오 유틸 없음.
- **스코어 공식**: 변경 없음. `ScoredGasStation`은 "필드는 입력만, 파생은 계산 프로퍼티" 원칙으로 리팩토링(로직 동일).
- **신규 도메인**: `CandidateSelectionStage` enum, `RouteRecommendationResult` data class.
- **신규 상수** (정적, 통일):
  - `N_THRESHOLD = 30`
  - `HARD_CAP = 30`
  - `MBR_BUFFER_METERS = 5_000.0`
  - `TIGHT_CORRIDOR_METERS = 2_000.0`
- **반환 타입**: `List<ScoredGasStation>` → `RouteRecommendationResult(baseRoute, scored, selectionStage, candidatesCollected, kakaoCallsMade)`.
- **공개 API**: 요청 파라미터 변경 없음, 응답에 wrapper DTO 도입으로 메타정보 노출.

---

## 1. 도메인 변경

### 1.1 필드 원칙

> `ScoredGasStation`의 저장 필드는 **계산 불가능한 입력값만** 담는다. 파생 값은 계산 프로퍼티(`val get()`)로 노출한다.

### 1.2 `ScoredGasStation` 재설계

**저장 필드 (uncomputable 입력)**:

```kotlin
class ScoredGasStation(
    val station: GasStation,
    val price: Int,
    val detourDistanceMeters: Double,
    val detourSeconds: Int,
    val refuelLiters: Double,
    val fuelEfficiency: Double,
    val isActualDetour: Boolean
)
```

**계산 프로퍼티 (derived)**:

```kotlin
val detourKm: Double
    get() = detourDistanceMeters / METERS_PER_KM

val fuelCost: Double
    get() = price * refuelLiters

val detourFuelCost: Double
    get() = (detourKm / fuelEfficiency) * price

val detourTimeCost: Double
    get() = (detourSeconds / SECONDS_PER_HOUR) * MINIMUM_WAGE_PER_HOUR

val score: Double
    get() = fuelCost + detourFuelCost + detourTimeCost
```

**공식 (기존과 동일)**:

```
score = price × refuelLiters
      + (detourKm / fuelEfficiency) × price
      + (detourSec / 3600) × MIN_WAGE
```

`ScoredGasStation.of()` 팩토리는 제거하고 생성자 직접 호출로 전환한다. 반경 기반 추천 등 다른 호출부도 함께 마이그레이션.

### 1.3 `CandidateSelectionStage` (신규 enum)

```kotlin
package com.kumoh.lbs.gasstation.domain

enum class CandidateSelectionStage {
    POLYLINE_MBR,     // 1단계: polyline MBR + buffer, 가장 관대
    TIGHT_CORRIDOR,   // 2단계: polyline distance ≤ 2km 필터
    PRICE_CAPPED      // 3단계: 가격 상위 HARD_CAP개
}
```

### 1.4 `RouteRecommendationResult` (신규 data class)

```kotlin
data class RouteRecommendationResult(
    val baseRoute: Route,
    val scored: List<ScoredGasStation>,
    val selectionStage: CandidateSelectionStage,
    val candidatesCollected: Int,
    val kakaoCallsMade: Int
)
```

---

## 2. 서비스 변경

### 2.1 상수

```kotlin
companion object {
    private const val N_THRESHOLD = 30
    private const val HARD_CAP = 30
    private const val MBR_BUFFER_METERS = 5_000.0
    private const val TIGHT_CORRIDOR_METERS = 2_000.0
}
```

### 2.2 `recommend` 시그니처

```kotlin
fun recommend(
    origin: Coordinate,
    destination: Coordinate,
    fuelType: FuelType,
    refuelLiters: Double,
    fuelEfficiency: Double,
    limit: Int
): RouteRecommendationResult
```

### 2.3 후보 수집 cascade (`gatherCandidates`)

```
1. baseRoute 확보 (기존과 동일)

2. 1단계 — POLYLINE_MBR:
   bounds = BoundingBox.aroundPolyline(baseRoute.polyline, MBR_BUFFER_METERS)
   stations = repo.findInBounds(bounds)
   stage = POLYLINE_MBR

3. 2단계 — TIGHT_CORRIDOR:
   if stations.size > N_THRESHOLD:
       stations = stations.filter {
           GeoUtils.calculateMinDistanceToPolyline(it.coordinate, base.polyline)
               <= TIGHT_CORRIDOR_METERS
       }
       stage = TIGHT_CORRIDOR

4. 가격 결합 (기존 attachPricesAndDropMissing 로직):
   withPrice = prices 매핑 후 가격 오름차순 정렬, 가격 없는 주유소 drop + 경고

5. 3단계 — PRICE_CAPPED:
   if withPrice.size > HARD_CAP:
       withPrice = withPrice.take(HARD_CAP)
       stage = PRICE_CAPPED

6. logger.info { "후보 수집 단계: $stage, 최종 후보 수: ${withPrice.size}" }
   return CandidatePool(withPrice, stage)
```

**주의**:
- 1·2단계 판정은 가격 결합 **전** stations 수 기준 (DB 쿼리 직후 빠른 판정)
- 3단계만 가격 결합 **후** 판정 (가격 기준 take 필요)
- 기존 `BUFFER_RADIUS_METERS = 2000.0` 상수는 `TIGHT_CORRIDOR_METERS`로 이관

### 2.4 `rankByPriceLowerBound` 시그니처 조정

```kotlin
private fun rankByPriceLowerBound(
    candidates: List<Pair<GasStation, Int>>,
    baseRoute: Route,
    origin: Coordinate,
    destination: Coordinate,
    refuelLiters: Double,
    fuelEfficiency: Double,
    limit: Int
): Pair<List<ScoredGasStation>, Int /* kakaoCallsMade */>
```

- pruning 로직은 그대로 유지
- `ScoredGasStation` 생성은 생성자 직접 호출
- Kakao 경유 호출 수를 반환 (실제 호출 횟수, pruning 후 break 전 누적)

### 2.5 지오 유틸

- **신규 유틸 없음**. 기존 `BoundingBox.aroundPolyline`, `GeoUtils.calculateMinDistanceToPolyline` 재사용.

---

## 3. 응답 DTO (②)

### 3.1 `GasStationResponse` 확장

```kotlin
data class GasStationResponse(
    // 기존
    val opinetStationId: String,
    val name: String,
    val brand: String,
    val latitude: Double,
    val longitude: Double,
    val price: Int,
    val distance: Double,              // detourDistanceMeters (호환 alias)
    val durationSeconds: Int,          // detourSeconds (호환 alias)
    val score: Double,
    val isActualDetour: Boolean,
    // 신규
    val isSelf: Boolean,
    val estimatedFuelCost: Int,        // price × liters
    val estimatedDetourCost: Int,      // detourFuelCost + detourTimeCost
    val estimatedSavings: Int?,        // 후보군 최고가 대비 절감액
    val rankReason: String
)
```

### 3.2 `RouteRecommendationResponse` wrapper (신규)

```kotlin
data class RouteRecommendationResponse(
    val selectionStage: String,        // POLYLINE_MBR / TIGHT_CORRIDOR / PRICE_CAPPED
    val candidatesCollected: Int,
    val kakaoCallsMade: Int,
    val baseRouteDistanceMeters: Int,
    val baseRouteSeconds: Int,
    val recommendations: List<GasStationResponse>
)
```

공개 API 스펙 breaking 변경이나, 논문성 서비스 성격상 수용. 호환 유지가 필요한 경우 `/recommendations/route/v2`로 분기 고려 (향후 결정).

### 3.3 `rankReason` 생성 규칙

```
parts = []
parts += "우회 ${"%.1f".format(detourKm)}km"
if rank == 1:                  parts += "최적 스코어"
if isCheapestInCandidates:     parts += "후보 최저가"
return parts.joinToString(" · ")
```

### 3.4 Assembler

```kotlin
object GasStationResponseAssembler {
    fun build(
        result: RouteRecommendationResult,
        limit: Int
    ): RouteRecommendationResponse
}
```

내부에서 rank·최저가 판정·estimatedSavings 계산 일괄 수행.

---

## 4. 컨트롤러 변경

- `/api/gas-stations/recommendations/route` 요청 파라미터 변경 없음
- 반환 타입: `List<GasStationResponse>` → `RouteRecommendationResponse`
- 컨트롤러는 `recommender.recommend(...)` 결과를 assembler에 위임

---

## 5. 파일별 변경 목록

| 파일 | 변경 |
|---|---|
| `gasstation/domain/ScoredGasStation.kt` | 필드 원칙 적용, 계산 프로퍼티 전환, `of()` 제거 |
| `gasstation/domain/CandidateSelectionStage.kt` | 신규 enum |
| `gasstation/domain/RouteRecommendationResult.kt` | 신규 data class |
| `gasstation/service/GasStationRouteRecommender.kt` | polyline MBR cascade, 반환 타입 변경, 호출 수 카운트 |
| `gasstation/controller/GasStationController.kt` | wrapper DTO 반환, assembler 호출 |
| `gasstation/controller/GasStationResponse.kt` | 필드 확장, wrapper DTO 추가, assembler |
| `docs/references.md` | Kelley & Kuby 2013 등 인용 추가 |
| `docs/recommendation-flow.md` | polyline MBR cascade + 응답 필드 반영, TL;DR 추가 |

---

## 6. 구현 순서

1. `ScoredGasStation` 계산 프로퍼티 리팩토링 + `of()` 제거 + 호출부 마이그레이션 + 기존 테스트 회귀.
2. `CandidateSelectionStage`, `RouteRecommendationResult` 신규 생성.
3. `GasStationRouteRecommender` cascade 구현 + 단위/통합 테스트.
4. `GasStationResponse` 확장 + `RouteRecommendationResponse` wrapper + assembler + Controller 연결.
5. `docs/references.md` 및 `docs/recommendation-flow.md` 업데이트.

## 7. 회귀 안전장치

- 스코어 공식은 동일 → 수식 검증 테스트 그대로 통과해야 함
- `ScoredGasStation` 생성자 변경으로 **반경 기반 추천 등 다른 호출부도 영향** → 생성자 직접 호출로 마이그레이션 필요
- 기존 corridor 2km 전제 테스트는 1단계 POLYLINE_MBR(5km buffer)에서 후보가 늘어날 수 있으므로 기대값 보정 필요
- `HARD_CAP`은 Kakao 호출 수 **절대 상한** 보장 — 쿼터 안정성 확보

## 8. 향후 개선 키워드 (Future Work)

본 설계는 complexity를 피하기 위해 정적 상수를 사용한다. 실험 데이터가 모이면 아래 항목을 재검토:

- **동적 buffer 스케일링**
  - `MBR_BUFFER_METERS`를 `tripDistance`의 선형/sqrt 함수로 치환
  - `TIGHT_CORRIDOR_METERS`도 마찬가지
  - 논의된 공식 예시:
    - `mbrBuffer = clamp(tripKm × 0.10, 2, 15)` (선형)
    - `corridor = clamp(sqrt(tripKm) × 0.4 + 2, 2, 6)` (sqrt)
- **cascade 단계 확장**: 3단계 → 5단계 (medium corridor 추가)
- **cascade 결정 지표**: 후보 수 대신 예상 Kakao 호출 수 기반
- **주유소 밀도 지표** 사전 계산해 cascade 분기에 활용
- **`RouteRecommendationResponse` v2 분기** 여부 (breaking change를 완화하려면)

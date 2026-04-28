# 경로 기반 주유소 추천 개선 실행 계획

> ⚠️ **구버전 개선 계획 기록**: 이 문서는 거리 기반 `TIGHT_CORRIDOR`와 `N_THRESHOLD = HARD_CAP = 30` 통일 논의 등 옛 cascade 컨텍스트의 개선 계획입니다. 현재 구현은 식 (2) 가격 하한에 직접 연결되는 `ROUTE_PRICE_CEILING`(N에 무관하게 항상 적용)으로 진화하여 본 문서의 N_THRESHOLD 의존이 사라졌습니다. 현재 cascade는 `CLAUDE.md`와 `docs/paper/route-focus-submission.md` Ⅲ.4를 참고하세요. 이 문서는 개선 결정 과정의 기록으로 보존됩니다.

## TL;DR

- **문제**: 현재 후보 수집은 고정 2km polyline corridor를 엄격히 적용해 false negative(polyline에서 3km 떨어져도 실제 우회는 500m인 주유소를 미리 제외)를 만든다. Kakao 호출 전까지 모든 선별은 근사이므로, 1차 수집은 최대한 관대해야 한다.
- **핵심 인사이트**: **polyline MBR**(카카오가 돌려준 실제 경로 점들을 모두 감싸는 bounding box)을 기반으로 하면, 경로 곡률이 자동으로 반영된다. origin-destination MBR처럼 "동적 buffer로 곡률 커버"를 수동 계산할 필요가 없다.
- **접근**: **polyline MBR + 넉넉한 buffer**로 1차 수집하고, 후보 수가 임계값(30)을 넘으면 점진적으로 좁히는 **adaptive cascade**를 적용한다.
- **cascade**: `polyline MBR + 5km buffer` → `polyline distance ≤ 2km 필터` → `가격 상위 30개 하드캡`. 총 3단계.
- **스코어**: Kakao 실측 거리·시간 기반의 현재 공식(`가격×주유량 + 우회 연료비 + 우회 시간비`)을 **그대로 유지**. 문헌 고찰(Kelley & Kuby 2013)상 detour 최소화가 지배적 선호이며, 비선형 페널티·골든존 등 추가 가중은 근거가 부족하다.
- **응답 DTO 풍부화**: 실우회 증분, 예상 절감액, `rankReason`, cascade 메타 정보를 추가.
- **하위 호환**: 공개 API 요청 파라미터 변경 없음, 응답 필드는 추가만.

---

## 배경

### 기존 흐름의 한계

현재 `GasStationRouteRecommender`는 Kakao 기본 경로를 조회한 뒤 **"polyline 주변 2km 이내"** 주유소만 후보로 삼는다(`GasStationRouteRecommender.kt:55-59`). 문제는:

- Corridor 필터는 **"polyline 거리 ≈ 실제 우회 거리"라는 근사**에 의존
- polyline에서 straight-line 3km 떨어져도 진출입 구조·고속도로 연결 덕에 실제 우회 500m일 수 있음
- 이 경우 **더 좋은 후보를 검증조차 하지 않고 제외**(false negative)
- 고정 2km는 이동 거리·지역 밀도 차이를 반영하지 못함

**핵심 원칙**: Kakao 호출 전 모든 선별은 근사다. 따라서 **1차 수집은 가능한 한 관대**해야 하고, 폭발이 났을 때만 점진적으로 좁혀야 한다.

### 문헌 고찰 — 스코어 가정의 재검토

이전 초안에서는 "비선형 우회 페널티"와 "골든존 스코어링"을 제안 알고리즘에 포함시키려 했으나, 문헌 고찰 결과:

- **Kelley & Kuby (2013)**: 드라이버는 집 근처보다 "on the way" 주유소를 **10:1**로, fleet 드라이버는 편차 최소 주유소를 **6:1**로 선택. **Detour 최소화가 지배적 선호**.
- 가솔린 드라이버가 출발/도착 근처를 선호한다는 약한 증거는 있으나, detour 최소화의 부산물로 해석 가능. 독립적 가중의 근거는 부족.
- 수용 가능 우회 범위: 개인 평일 ~1,750m, 주말 ~750m, 상용 ~500m, CNG 5~6분 무차별.

**결론**: 현재 스코어 공식은 이미 Kakao 실측 기반의 합리적 비용 함수이며, 그 위에 덧씌울 검증된 가중 함수는 없다. **스코어 공식은 변경하지 않는다.**

---

## 개선 항목 (2개)

### ① Polyline MBR-first Adaptive Cascade

**현재**: 고정 2km polyline corridor.

**문제**: polyline 거리 기반 엄격 필터가 false negative를 만들고, 고정 반경이 거리·밀도 차이를 반영 못함.

**개선 방향**: polyline MBR에 넉넉한 buffer를 붙여 **1차는 관대하게** 수집하고, 후보 개수 N을 직접 관찰해 cascade로 점진 압축.

```
1단계: polyline MBR + 5km buffer로 수집
       (BoundingBox.aroundPolyline(polyline, 5000))
       ▸ if count ≤ 30: 사용

2단계: polyline distance ≤ 2km 필터 적용
       ▸ if count ≤ 30: 사용

3단계: 가격 오름차순 상위 30개 하드캡
```

**왜 polyline MBR인가**:
- Kakao가 돌려준 **실제 경로 polyline**의 bounding box를 쓰면 경로 곡률이 자동으로 반영됨
- origin-destination MBR처럼 "직사각형이 곡률을 못 커버하니 동적 buffer로 보정"하는 논의 자체가 사라짐
- 수학적으로 polyline MBR ⊇ origin-destination MBR이므로 곡선 경로에서 항상 더 넓거나 같음
- 기존 `BoundingBox.aroundPolyline` 유틸을 그대로 재사용

**왜 3단계인가**:
- 1단계는 "관대한 수집" — polyline MBR로 dead zone(L/U자 경로 내부 빈 공간)까지 포함, 곡률 커버
- 2단계는 "polyline 거리로 dead zone 제거" — 1단계의 MBR 직사각형 안 대부분의 관련 없는 후보가 여기서 걸러짐
- 3단계는 "최후 안전장치" — 여전히 많으면 가격 상위 K로 하드캡, Kakao 호출 수 절대 상한 보장

**왜 N_THRESHOLD = HARD_CAP = 30인가**:
- 30회 Kakao 호출은 "괜찮은 주유소 30곳의 실제 경로를 검증" 수준으로 충분히 의미 있음
- 쿼터/지연 모두 안정권
- N_THRESHOLD와 HARD_CAP을 통일하면 파라미터 수가 줄고 설명이 단순해짐

**기대 효과**:
- 단거리·저밀도: 1단계에서 확정 → false negative 거의 없음
- 장거리·고밀도: 2~3단계 발동 → API 비용 제어
- cascade 단계가 로그·응답에 기록 → 실험 분석 가능

**논문적 가치**: 기존 연구는 corridor를 고정값으로 쓴다. 본 접근은 "polyline MBR을 inclusive 1차 수집으로 쓰고, 후보 수 기반으로 진행 단계를 동적 선택"하는 기여로 제시 가능하다.

---

### ② 응답 DTO 풍부화

**현재**: `distance`, `durationSeconds`, `score`, `isActualDetour`만 노출.

**문제**: 추천 결과의 근거가 드러나지 않는다. 논문 case study·시스템 평가에 필요한 수치도 없다.

**개선 방향**:

개별 주유소:
- `estimatedFuelCost` — 주유 비용(price × liters)
- `estimatedDetourCost` — 우회 연료비 + 시간비
- `estimatedSavings` — 후보군 최고가 대비 절감액
- `isSelf` — 셀프 여부
- `rankReason` — "우회 0.8km · 후보 최저가" 같은 설명 문구

응답 최상위 메타 wrapper:
- `selectionStage` — cascade 어느 단계에서 확정됐는지
- `candidatesCollected` — 최종 후보 수
- `kakaoCallsMade` — 실제 경유 경로 호출 수
- `baseRouteDistanceMeters`, `baseRouteSeconds` — 기본 경로 기준
- `recommendations` — 개별 주유소 리스트

**기대 효과**: 사용자 어필과 동시에 **논문 case study 표·시스템 분석**에 바로 쓰이는 수치 노출.

---

## 우선순위 및 의존성

```
① Polyline MBR Cascade ──► ② 응답 DTO 풍부화
```

1단계에서 cascade 메타가 생성되고, 2단계에서 응답에 반영된다.

## 비범위 (Out of Scope)

- **스코어 공식 변경** — 문헌상 근거 부족, 현재 공식이 이미 합리적
- **사용자 모드 분리** (PRICE/ROUTE/BALANCED) — 논문성 서비스에 불필요
- **OPINET Top20 API** — 별도 논의
- **pruning 로직 변경** — 기존 price lower-bound pruning 유지

## 향후 개선 (Future Work) — 잊지 말 것

아래는 본 계획에서 **정적 상수로 단순화했지만 향후 실험·분석 결과에 따라 재검토할 여지가 있는 키워드**들이다. 지금은 complexity를 피하기 위해 정적으로 가지만, 실사용 데이터가 모이면 적용 고려.

- **동적 buffer** (MBR 1단계 buffer, corridor 2단계 반경)
  - 이동 거리에 비례한 선형/sqrt 스케일링
  - 예: `mbrBuffer = clamp(tripKm × 0.10, 2, 15)`, `corridorWide = clamp(sqrt(tripKm) × 0.4 + 2, 2, 6)`
  - 논의 맥락: origin-destination MBR 기반일 때 제기된 아이디어였으나, polyline MBR 채택으로 MBR 부분은 필요성이 많이 줄어듦. corridor는 여전히 동적화 여지.
- **cascade 단계 수 확장**: 현재 3단계 → 5단계로 세분화 (medium corridor 등)
- **cascade 단계를 후보 수가 아닌 예상 Kakao 호출 수 기반으로 판정**
- **주유소 밀도 지표**를 DB에 미리 계산해 cascade 결정에 활용

## 리스크

- **N_THRESHOLD·HARD_CAP 튜닝**: 초기값 30은 경험적 추정. 실험으로 확정 필요.
- **polyline MBR의 dead zone**: L/U자 경로에서 직사각형 내부 빈 공간 후보가 1단계에 섞여 들어옴. 2단계 polyline distance 필터로 자연스럽게 해결되나, 2단계 발동률이 예상보다 높을 수 있음.
- **회귀**: 기존 테스트는 고정 2km corridor 전제로 작성됨. polyline MBR 단계에서 결과가 확장될 수 있어 보정 필요.

## 테스트 전략

- **단위**: `ScoredGasStation` 계산 프로퍼티 리팩토링 회귀, cascade 분기 로직.
- **통합**: 3가지 시나리오
  1. 도심 단거리 (후보 소수) → 1단계 MBR 확정
  2. 중거리 (후보 수십~수백) → 2단계 corridor 확정
  3. 장거리 고밀도 (후보 수천) → 3단계 PRICE_CAPPED 확정
- **회귀**: 수식 검증은 동일해야 함. 후보 집합이 달라지는 케이스는 테스트 보정.

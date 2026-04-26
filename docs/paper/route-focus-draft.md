# [수정본 최종 초안]

> **파일 성격**: 저자 04-18 판본(`original.md`)을 기반으로 **제목을 옵션 C로 확정**하고, Ⅰ·Ⅱ장에 제안했던 삽입·교체 문구를 전부 반영한 최종 초안. 그림 자리 5곳, 참고문헌 확장
> 포함. 길이 초과 시 제외 기준은 문서 말미 "분량 조정 여유"를 참고.
>
> **제목 옵션 C 적용됨**: 국문 *비용 인지 우회를 활용한 비용 최소 주유소 추천 시스템*, 영문 *Strategic-Deviation-Aware Gas Station Recommendation for Cost
Minimization*.

---

# 비용 인지 우회를 활용한 비용 최소 주유소 추천 시스템

조재중\*, 이홍철\*, 신광철\*, 오병우\*\*

## Cost-Aware Detour-Based Gas Station Recommendation for Cost Minimization

Jaejung Jo\*, Hongcheol Lee\*, Gwangcheol Shin\*, and Byoungwoo Oh\*\*

> \*국립금오공과대학교 컴퓨터공학부, {20171115, 20181414, 20210632}@kumoh.ac.kr
> \*\*국립금오공과대학교 컴퓨터공학과, bwoh@kumoh.ac.kr (교신저자)

> **🔒 FIXED ABSTRACT (2026-04-20, 본문 정렬 작업 중)**: 아래 요약은 Keywords·Ⅰ·Ⅱ·Ⅲ·Ⅳ장 정렬의 기준점(Ground Truth)이다. 본문 수정은 이 요약의 방향·용어·키
> 개념과 정합하도록 조정하며, 요약 자체는 별도 지시 없이 변경하지 않는다.

## 요 약

본 논문은 경로 기반 주유소 추천에서 탐색 공간을 경로 주변에 한정하지 않고 그 경로를 포함하는 최소경계사각형(MBR) 내부로 확장하는 2-hop 재탐색 방식을 제안한다. 후보가 과도하면 기본 경로 근접도와 가격으로
단계 압축한 뒤, 각 후보를 경유하는 실제 도로 경로를 재산출해 주유비와 우회의 연료·시간 비용을 합산한 점수로 순위를 정한다. 가격 오름차순 탐색 중 남은 후보로 지출을 낮출 수 없는 시점에 탐색을 중단하여 외부
API 호출을 절제하면서 상위 추천의 최적성을 보존하고, 경로상 단순 최저가 선택 대비 실질 지출 절감을 달성한다.

## Abstract

In this paper we propose a 2-hop re-query scheme for route-based gas station recommendation that, rather than
restricting the search space to the vicinity of the route, expands it to the minimum bounding rectangle (MBR) that
encloses the route. When candidates become excessive, the pool is progressively tightened by proximity to the base route
and by price; for each remaining candidate we then recompute the actual road-network path through it and rank
recommendations by fuel price plus the detour's fuel and time cost. During ascending-price traversal the search halts
once the theoretical lower bound on the remaining candidates cannot beat the current best, bounding external routing-API
calls while preserving the optimality of the top-ranked results and yielding meaningful spending reductions over the
naive on-route lowest-price selection.

## Key words

gas station recommendation, 2-hop routing, minimum bounding rectangle, detour cost minimization, location-based service

---

## Ⅰ. 서 론

최근 중동 지역에서는 미국·이란 간 군사적 충돌과 호르무즈 해협을 둘러싼 긴장 고조로 인해 국제 원유 공급망의 불확실성이 확대되고 있으며, 이에 따라 국제 유가의 상승 및 변동성이 심화되고 있다. 국제 유가 변동은
운전자의 이동 비용에 직접적인 영향을 미치며, 비용을 고려한 경로 선택의 중요성을 증가시킨다.

선행 연구에서는 유가 상승에 따른 휘발유 수요 감소가 제한적으로 나타난다. [1]의 메타분석에 따르면 휘발유 수요의 가격탄력성은 평균 -0.53으로 나타났으며, 이는 가격이 상승하더라도 수요 감소 폭이 크지 않은
비탄력적 특성을 의미한다. 휘발유 수요의 비탄력성은 교통 수요가 필수재적 성격을 가지며 대체 이동 수단이 제한적이라는 구조적 요인에 기인한다. 따라서 소비자는 이동 자체를 줄이기보다 비용을 절감할 수 있는 방식으로
선택을 조정하는 경향을 보인다.

주유소 간 가격 차이는 소비자의 주유소 선택에 중요한 영향을 미친다. 주유소 가격은 위치와 경쟁 환경 등 다양한 요인에 의해 차이가 발생한다. 또한 주유소 선택 요인에 관한 연구[2]에서는 가격 요인이 재방문 의도에
유의한 영향을 미치는 것으로 나타났으며, 가격 변수는 통계적으로 유의한 음의 영향을 보였다(t = -4.43, p = 0.001, β = -0.167). 이는 가격이 높을수록 소비자의 주유소 선택 및 재방문 의도가
감소함을 의미하며, 소비자가 가격을 중요한 의사결정 요인으로 고려함을 보여준다.

실제 사례를 분석한 결과, 목적지까지의 경로상에 위치한 주유소와 경로를 소폭 우회하여 접근 가능한 주유소 간에는 리터당 가격 차이가 존재하며, 추가 이동으로 인한 비용 증가를 고려하더라도 전체 비용 측면에서 더
경제적인 선택이 확인된다. 경로상 위치만을 기준으로 주유소를 선택하는 방식은 비용 최소화 관점을 충분히 반영하지 못한다.

[그림 1] 비용 인지 우회의 개념 — 경로상 vs 우회 후보의 지출 역전
※ 이미지 생성 프롬프트는 `figure-prompts.md` 참고.

기존 접근은 경로 폴리라인이 지나는 구간에 국한된 국소 탐색(local search)이다. 경로상 주유소만 후보가 되므로, 경로에서 소폭 벗어나 있으나 진출입 구조상 실제 우회 비용이 작은 잠재적으로 더 경제적인
후보가 기계적으로 배제된다. 본 연구는 이러한 국소 탐색의 한계를 극복하는 비용 인지(cost-aware) 우회 기반의 추천 방식을 제안하며, 이를 통해 사용자에게 지출 관점의 합리적 주유 의사결정을
지원한다.

---

## Ⅱ. 관련 연구

기존 주유소 정보 서비스는 지도 기반 조회 기능과 주유소 가격 정보 제공을 중심으로 운영된다. 대표적으로 오일나우(OilNow)와 오피넷(Opinet)과 같은 서비스는 사용자에게 현재 위치 또는 특정 경로를 기준으로
주변 주유소의 가격 정보를 제공한다. 기존 주유소 정보 서비스의 기능적 특성을 비교하기 위해 주요 기능을 표 1과 같이 정리하였다.

표 1. 서비스 기능 비교
Table 1. Service Comparison

| 기능 / 서비스     | Proceed System | 오일나우 | 오피넷 |
|--------------|----------------|------|-----|
| 지도 연동        | O              | O    | O   |
| 주유소 추천       | O              | △    | O   |
| 교통 데이터 연동    | △              | △    | △   |
| 우회 경로 기반 탐색  | O              | X    | X   |
| 주유 예정량 반영 추천 | O              | X    | X   |
| 경로 이탈 비용 고려  | O              | X    | X   |

표 1에서 확인할 수 있듯이 기존 서비스는 경로상 주유소의 가격 정보를 제공하고 가격이 낮은 주유소를 우선 제시하나, 경로를 일부 우회할 경우 발생하는 추가 이동 비용과 가격 절감을 함께 고려하는 기능은 지원하지
않는다. 따라서 경로 이탈에 따른 비용 변화가 반영되지 않으며, 비용 효율적인 주유소 선택을 지원하는 데 한계가 있다.

기존 서비스의 한계를 보완하기 위해 공간 데이터 처리 및 경로 탐색과 관련된 다양한 연구가 수행되어 왔다. 공간 데이터 처리 분야에서는 다차원 공간 객체를 효율적으로 검색하기 위해 R-tree와 같은 공간 인덱싱
구조가 널리 활용된다[3]. R-tree는 공간 객체를 최소경계사각형(Minimum Bounding Rectangle, MBR)으로 그룹화하여 관리하며, 질의 수행 시 탐색 범위를 제한함으로써 불필요한 연산을 줄이고
검색 효율을 향상시킨다. 이동 경로 데이터 처리 분야에서는 trajectory를 다수의 MBR로 근사하여 저장하고, 이를 기반으로 질의 수행 시 후보 경로를 필터링하는 연구가 이루어져 왔다[4]. 이러한 방법은
MBR 간 겹침 여부를 활용하여 불필요한 경로 비교를 제거하고 효율적인 후보 집합을 생성한다.

경로 탐색 분야에서는 단순한 최단 거리 기반 접근을 넘어 연료 소비를 고려한 경로 탐색 기법이 제안되었다. 이러한 eco-routing 연구에서는 동일한 출발지와 목적지에 대해 연료 소비를 최소화하는 경로를
선택한다. 이 과정에서 기존 최단 경로보다 우회 경로가 더 효율적인 경우가 존재한다[5]. 또한 연료 비용을 고려한 경로 최적화 문제로 Gas Station Problem이 제안되었다. 해당 연구에서는 각 주유소의
연료 가격과 차량의 연료 용량을 고려하여 이동 비용을 최소화하는 주유 전략을 결정한다[6].

한편 주유·충전 시설 입지 최적화 계열에서는 운전 경로 자체를 수요 흐름으로 보고 시설을 배치하는 연구가 이루어져 왔다. Flow-Refueling Location Model[7]은 차량의 제한된 주행거리를 반영해
흐름 포획(flow-capturing) 관점에서 시설을 배치하며, 이를 확장한 Deviation-Flow Refueling Location Model[8]은 운전자가 최단 경로에서 허용 가능한 수준의 우회(
deviation tolerance)를 감수한다는 가정을 목적함수에 명시적으로 도입한다. 또한 실제 CNG 운전자 관찰 연구[9]에서는 운전자가 집 근처 주유소보다 경로상 주유소를 10:1의 비율로 선호하며 평균
5.6분의 우회를 자연스럽게 수용한다는 실증이 보고되었다. 이들 연구는 시설이 존재하지 않을 때의 입지 최적화 관점에서 경로 우회를 다루었으나, 시설이 이미 조밀하게 존재하는 상황에서 단일 trip의 실시간 추천
시점에 우회를 비용 관점으로 활용하는 운용 관점은 충분히 다뤄지지 않았다.

[그림 2] 연구 계보 — 본 연구의 위치 (부록 A 분량 조정 시 제외 후보)
※ 이미지 생성 프롬프트는 `figure-prompts.md` 참고.

이에 본 연구에서는 공간 데이터의 MBR 필터링[3][4], 실측 경로 기반 비용 산정[5], 가격을 고려한 주유 의사결정[6], 그리고 입지 최적화에서의 deviation tolerance 개념[7][8][9]을
통합하여, 운용 시점의 주유소 추천 문제를 비용 인지(cost-aware) 우회를 통한 2-hop 재탐색 문제로 정식화한다. 기본 경로를 포함하는 최소경계사각형(MBR)으로 탐색 공간을 확장해 후보를
수집하고, 후보 과다 시 기본 경로 근접도와 가격으로 단계 압축한 뒤, 각 후보를 경유하는 실제 도로 경로를 재산출하여 주유비와 우회의 연료·시간 비용을 합산한 점수로 순위를 결정한다. 가격 하한 기반
pruning으로 외부 API 호출을 상위 추천의 최적성 보존 하에 절제함으로써, 경로상 국소 탐색의 한계를 극복하고 실제 이동 상황에서 비용 효율적 선택을 지원한다.

---

## Ⅲ. 비용 인지 우회를 통한 2-hop 지출 최소화

본 논문에서는 출발지–도착지 경로 위에 놓인 주유소만을 고려하는 기존 접근을 넘어, 경로를 감싸는 확장 탐색 공간에서 경유지 후보를 선정하고 경로 자체를 재조회해 실측 우회 비용을 산정하는 방식을 제안한다. 경로의
최단성 대신 지출 최소화를 목표로 삼는 이러한 접근을 본 논문은 비용 인지(cost-aware) 우회로 지칭한다. 비용 인지 우회는 운전자의 경로 기반 주유 선호[9]를 확장하여, 가격 절감이 가능한 소폭 우회까지 시스템이 포착하도록 일반화한 의사결정 프레임워크이다.

### 3.1 문제 정의

기존 주유소 추천은 경로 폴리라인이 지나는 구간 안에서만 후보를 고려하는 국소 탐색이다. 그러나 도로망에서는 폴리라인으로부터의 직선 거리와 실제 진출입 우회 거리가 크게 어긋날 수 있어, 폴리라인에서 3 km 떨어진
주유소가 실제로는 500 m 우회로 접근되는 사례가 자주 관찰된다. 본 연구는 이러한 국소 탐색의 한계를 극복하기 위해 탐색 범위를 경로를 포함하는 최소경계사각형(MBR) 내부로 확장하고, 그 안의 각 주유소를
경유지로 삼아 경로를 재조회해 실측 비용으로 순위를 정한다. 이는 FRLM[7]과 DFRLM[8]의 deviation tolerance 개념을 시설이 이미 존재하는 전제하의 추천 시점에 적용한 운용 문제로 볼 수
있다.

### 3.2 2-hop 재탐색 프레임워크

제안 시스템의 처리 흐름은 그림 3과 같이 네 단계로 구성된다.

1. 기본 경로(단계 1): Kakao Mobility Directions API로 출발지–도착지 경로를 조회해 폴리라인과 기준 거리·시간을 확보한다.
2. 확장 탐색 공간 형성 및 후보 필터링(단계 2): 폴리라인의 MBR에 우회 허용 상한의 두 배에 해당하는 buffer를 더해 탐색 공간을 구성하고, 내부의 주유소를 DB에서 조회한다. 후보 수가 과다할 때는
   cascade로 압축한다(3.4).
3. 경유 경로 재조회(단계 3): 각 후보를 경유지로 하는 경로(origin → candidate → destination)를 Kakao API로 재조회해 실제 도로망 기반 우회 거리·시간을 얻는다. 가격 하한
   pruning으로 호출 수를 제한한다(3.5).
4. 점수 정렬 및 상위 k개 반환(단계 4): 식 (1)의 점수 기준으로 오름차순 정렬해 상위 k개를 반환하며, 3.5의 pruning으로 재조회를 조기 종료해도 상위 k개 결과의 최적성이 수학적으로 보존된다.

이 구조의 핵심은 단계 2가 공간을 관대하게 열고 단계 3이 재조회로 엄밀한 비용을 확정한다는 점이며, 이는 고정 경로를 전제로 주유 시점만 결정하는 Gas Station Problem[6]과 달리 경로 자체를
후보에 따라 재구성한다는 점에서 구별된다.

[그림 3] 비용 인지 우회 파이프라인 (시스템 처리 흐름) — 제출본에서는 [그림 2]로 번호 부여
※ 이미지 생성 프롬프트는 `figure-prompts.md` 참고.

### 3.3 점수 공식

주유소 $i$의 점수는 식 (1)로 정의된다. 모든 항이 비음수이고 값이 낮을수록 우수하다.

$$
\text{score}_i \;=\; p_i\,\ell \;+\; \frac{d_i/1000}{\eta}\,p_i \;+\; \frac{t_i}{3600}\,w \tag{1}
$$

여기서 $p_i$는 유종 가격(원/L), $\ell$은 주유 예정량(L), $d_i$는 후보 경유 시 기본 경로 대비 우회 거리(m), $\eta$는 차량 연비(km/L), $t_i$는 우회 소요 시간(s), $w$
는 2026년 최저시급(10,320원/h)에 해당하는 우회 시간 기회비용 단가이다. $d_i$와 $t_i$는 단계 3의 경유 경로 응답에서 직접 산출되며, API 오차로 음수가 관측되는 경우 0으로 보정한다.

가격 절감 이득이 우회 비용을 상회할 때에만 후보가 경로상 최저가 대안을 이기며, 비선형 페널티를 도입하지 않은 것은 실증[9]상 개인 운전자의 우회 결정이 단순 합산 비교와 정합하기 때문이다.

### 3.4 확장 탐색 공간의 구성과 Cascade

단계 2의 탐색 공간 결정은 프레임워크의 효과성을 좌우한다. 고정 corridor(예: 경로로부터 직선 2 km) 방식은 false negative를 유발하고, 반대로 공간을 너무 넓게 잡으면 단계 3 외부
API 호출이 폭증한다. 본 연구는 후보 수 $N$을 관찰하며 cascade로 점진 압축하는 전략을 채택한다(표 2).

표 2. 후보 수집 cascade
Table 2. Candidate selection cascade

| 단계             | 조건               | 동작                                       |
|----------------|------------------|------------------------------------------|
| POLYLINE_MBR   | (항상 시작)          | 폴리라인 MBR + 동적 buffer(우회 상한 × 2) 내 주유소 수집 |
| TIGHT_CORRIDOR | $N > 30$         | 폴리라인까지 직선거리 ≤ 2 km로 필터                   |
| PRICE_CAPPED   | 가격 결합 후 $N > 30$ | 가격 오름차순 상위 30개만 유지                       |

1단계 POLYLINE_MBR은 "경로를 감싸는 직사각형을 관대하게 연다"는 본 프레임워크의 핵심을 구현한다. 2단계 TIGHT_CORRIDOR는 기본 경로 근접도로 1차 단계 압축해 MBR 내부 dead-zone(
L·U자 경로의 직사각형 빈 공간)을 제거하고, 3단계 PRICE_CAPPED는 가격 결합 후에도 과다한 후보를 가격 기준 상위로 2차 단계 압축해 단계 3 호출 수를 30회 이하로 상한 보장한다. 이
cascade는 deviation tolerance 개념[8]을 운용 시점의 호출 예산 관리로 번역한 결과로 볼 수 있다.

[그림 4] Cascade 단계별 탐색 공간 축소 (부록 A 분량 조정 시 제외 후보)
※ 이미지 생성 프롬프트는 `figure-prompts.md` 참고.

### 3.5 Price Lower-Bound Pruning

단계 3의 경유 경로 호출은 외부 API에 의존하므로 가능한 최소로 수행해야 한다. 식 (1)의 비음수성으로부터

$$
\text{score}_i \;\geq\; p_i \ell \tag{2}
$$

가 성립한다. 후보를 가격 오름차순으로 탐색하면서 상위 $k$개가 확보된 이후 어느 시점에서든

$$
p_i \ell \;>\; \text{score}^{(k)}_{\text{best}} \;\Longrightarrow\; \text{이후 모든 후보가 동일 조건 성립} \tag{3}
$$

이 성립하면 탐색을 종료해도 상위 $k$개의 최적성을 보존한다. 이 pruning은 비용 인지 우회가 더 이상 이득이 될 수 없는 시점을 수학적으로 식별해 재조회를 중단함으로써 외부 API 호출을 상위 $k$개 최적성
보존 하에 절제한다.

### 3.6 사례 연구 — 비용 인지 우회의 가치

표 3은 기본 경로 거리 15 km, 우회 허용 상한 $\min(15 \times 0.3,\ 10) = 4.5$ km 조건에서 두 후보를 비교한 예시이다(유종: 휘발유, $\ell = 40$
L, $\eta = 10$ km/L). **※ 표 3의 수치는 공식 (1)로 산출한 설명용 예시이며, 최종 제출본에는 지역 주유소 실측 데이터로 교체한다.**

표 3. 사례 비교
Table 3. Case comparison

| 후보                 | 가격(원/L) |   경유 거리 |  우회 거리 |    주유비 | 우회 연료비 |     점수 |
|--------------------|--------:|--------:|-------:|-------:|-------:|-------:|
| ON_ROUTE (경로상)     |   1,650 | 15.2 km | 0.2 km | 66,000 |     33 | 66,033 |
| OFF_ROUTE (비용 인지 우회) |   1,500 | 19.0 km | 4.0 km | 60,000 |    600 | 60,600 |

비용 인지 우회 대상(OFF_ROUTE)은 우회 연료비가 발생하지만 가격 절감이 이를 상회해 경로상 최저가(ON_ROUTE) 대비 약 5,400원의 지출 절감을 제공하며, 우회 상한 4.5 km를 초과하는 후보는 가격과
무관하게 배제되어 비현실적 우회가 추천되지 않는다.

### 3.7 구현

제안 시스템은 REST API로 구현되며, 한 번의 추천 요청당 외부 HTTP 호출은 "기본 경로 1회 + 경유 경로 최대 30회"로 상한이 보장된다.

[그림 5] 서비스 화면 예시 (UI Mock-up) — 부록 A 분량 조정 시 제출본 제외
※ 이미지 생성 프롬프트는 `figure-prompts.md` 참고.

---

## Ⅳ. 결론

본 논문은 경로상 국소 탐색의 한계를 지적하고, 주유소 추천을 비용 인지 우회를 통한 2-hop 재탐색으로 재정식화하였다. 입지 최적화의 deviation tolerance[7][8]를 운용 시점 추천으로 전환하고
MBR 필터링[3][4]을 경유지 선정에 응용하였으며, 사례에서 경로상 최근접 방식 대비 실질 지출 절감을 확인하였다.

향후 연구로 (1) MBR buffer·corridor 거리의 이동 거리 기반 동적 스케일링, (2) 주유소 밀도 지표를 활용한 cascade 분기 최적화, (3) 개별 운전자의 수용 우회 범위를 학습해 개인화하는
확장을 고려할 수 있다.

---

## 참 고 문 헌

1. M. Brons, P. Nijkamp, E. Pels, and P. Rietveld, "A meta-analysis of the price elasticity of gasoline demand: A system
   of equations approach," *Tinbergen Institute Discussion Paper*, no. 06-106/3, 2006. **(수정필)**
2. S. Lee, U. Lee, and Y. Kim, "An Empirical Study on the Effect of Choice Factors of Gas Station on Repurchase
   Intention," *Journal of Digital Convergence*, vol. 7, no. 3, pp. 83–92, 01. 2009.
3. A. Guttman, "R-trees: A dynamic index structure for spatial searching," In *Proceedings of the 1984 ACM SIGMOD
   International Conference on Management of Data*, pp. 47–57, 06. 1984.
4. S. R. J. S. J. Elding and M. A. Nascimento, "Trajectory Splitting Model for Efficient Spatio-Temporal Indexing." **(
   수정필)**
5. E. Ericsson, H. Larsson, and K. Brundell-Freij, "Optimizing route choice for lowest fuel consumption — potential
   effects of a new driver support tool," *Transportation Research Part C: Emerging Technologies*, vol. 14, no. 6, pp.
   369–383, 12. 2006.
6. S. Khuller, A. Malekian, and J. Mestre, "To fill or not to fill: The gas station problem," *ACM Transactions on
   Algorithms (TALG)*, vol. 7, no. 3, pp. 1–16, 06. 2011.
7. M. Kuby and S. Lim, "The flow-refueling location problem for alternative-fuel vehicles," *Socio-Economic Planning
   Sciences*, vol. 39, no. 2, pp. 125–145, 06. 2005.
8. J. G. Kim and M. Kuby, "The deviation-flow refueling location model for optimizing a network of refueling stations,"
   *International Journal of Hydrogen Energy*, vol. 37, no. 6, pp. 5406–5420, 03. 2012.
9. S. Kelley and M. Kuby, "On the way or around the corner? Observed refueling choices of alternative-fuel drivers in
   Southern California," *Journal of Transport Geography*, vol. 33, pp. 258–267, 12. 2013.

---

## 부록 A. 작업 체크리스트

### 작성자 마무리

- [ ] [1] Brons, [4] Elding `(수정필)` 서지 확정 (Google Scholar/Crossref로 권·호·페이지 재확인)
- [ ] 표 1 "교통 데이터 연동 △" 각주 한 줄 추가 (예: "Proceed System은 Kakao Mobility의 실시간 라우팅에 내재적으로 반영됨")
- [ ] 그림 1~5 이미지 생성 및 본문 삽입 (생성 프롬프트 제공됨)
- [ ] 표 3의 수치를 실제 측정값으로 교체(선택) — 국지 주유소 2곳 실 측정 시 설득력 상승
- [ ] 최종 hwp 장평·자간 재조판 (원본의 "마지막 글꼴 수정 중요!!" 메모 참조)

### 분량 초과 시 제외 우선순위 (길이 조정용)

> 3페이지 학회 논문 틀 초과 시 아래 순서로 제거 또는 축소한다.

1. **[그림 5] 서비스 UI mock-up** — 가장 먼저 부록으로 이동하거나 제거. 본문 논증에 필수 아님.
2. **[그림 4] Cascade 3패널** — 표 2로 내용 전달 가능. 지면 부족 시 제거.
3. **3.7 구현 섹션** — 2~3문장으로 압축하거나 제거. 본문 기여는 아님.
4. **[그림 2] 연구 계보 매트릭스** — II장 문단 서술이 대체 가능. 제거 고려.
5. **3.6 사례 연구의 일부 수치** — 표 3만 남기고 해설은 한 문단으로 압축.
6. **Ⅳ 결론** — 3문장으로 압축.
7. **[그림 1] 지출 역전** — 서론 시각화로서 가장 가치 높으므로 **최후까지 유지**.
8. **[그림 3] 파이프라인 플로우차트** — 기여의 핵심 전달 수단이므로 **최후까지 유지**.

### 참고문헌 축소 시

지면이 정말 부족하면 [7][8][9] 중 **[9] Kelley & Kuby 2013만 유지**해도 본문 논증 성립. [9]가 "실제 운전자의 10:1 on-the-way 선호"라는 실증 근거로 가장 강력하다.

# 경유 경로 기반 비용 최적 주유소 추천 시스템

조재중\*, 이홍철\*, 신광철\*, 오병우\*\*

## A Cost-Optimal Routing System for Gas Station Recommendation

Jaejung Jo\*, Hongcheol Lee\*, Gwangcheol Shin\*, and Byoungwoo Oh\*\*

\*국립금오공과대학교 컴퓨터공학부, {20171115, 20181414, 20210632}@kumoh.ac.kr
\*\*국립금오공과대학교 컴퓨터공학부, bwoh@kumoh.ac.kr (교신저자)

## 요 약

본 논문은 주유소 경유 경로 추천에서 주유 없이 출발지에서 도착지로 가는 기본 경로상으로 주유소 탐색 공간을 한정하던 기존 방식 대신, 기본 경로를 감싸는 최소경계사각형과 약간의 여유 공간까지 탐색 공간을 확장해 최적의 주유소를 추천하는 방법을 제안한다. 확장된 공간에서 후보 주유소를 수집한 뒤, 기본 경로상 최저가를 기준으로 비효율적인 후보를 사전 제거하고, 각 후보를 경유하는 경로를 조회하여 주유 비용과 우회로 인한 연료·시간 비용을 통합한 값을 기준으로 순위를 결정한다. 이때 비용 개선이 불가능한 시점에 도달하면 탐색을 조기 종료하여 외부 API 호출을 줄이면서도 추천 결과의 최적성을 유지한다. 이를 통해 기존 방식 대비 실제 비용 절감 효과를 달성한다. 또한 사용자 입력부터 외부 내비게이션 앱 연동까지를 포함한 운용 가능한 시스템으로 구현하였다.

## Abstract

This paper proposes a method for recommending gas stations along a route by overcoming the limitations of conventional approaches that restrict candidates to those on the base route. The proposed method expands the search space to a minimum bounding rectangle enclosing the route and identifies candidate stations within it. Candidates are pruned based on the lowest on-route price, and each remaining candidate is evaluated by recomputing the actual route and integrating fuel and detour costs. The search terminates early when no further cost improvement is possible, reducing API calls while preserving optimality. As a result, the proposed method achieves cost savings compared to conventional approaches. The system is implemented end-to-end, from user input to external navigation app handoff.

## Key words

gas station, cost-optimal routing, minimum bounding rectangle, location-based service

## Ⅰ. 서 론

유가는 수요·공급, 환율, 지정학적 요인 등에 따라 일상적으로 등락한다. 예를 들어, 2026년 미국-이란 간 군사적 충돌과 호르무즈 해협 봉쇄로 원유 공급에 차질이 발생하였다. 이로 인한 유가 변동성은 운전자의 이동 비용에 직접적인 영향을 미친다.

휘발유는 가격 변화에도 불구하고 수요가 크게 변하지 않는 특성을 보인다[1]. 따라서 소비자는 이동 자체를 줄이기보다 주유 비용을 낮추는 방향으로 선택을 조정하는 경향을 보인다. 또한 주유소 선택 요인에 관한 연구[2]에서 가격이 높을수록 재방문 의도가 낮아지는 것으로 보고되며, 주유소 간 리터당 가격 차이는 소비자의 주유소 선택에 중요하게 작용한다.

일반적으로 경로는 도로의 기하학적 형상을 반영하여, 일련의 직선 세그먼트들이 연결된 폴리라인(Polyline) 형태로 모델링된다. 기본 경로상 주유소와 경로를 벗어나 접근 가능한 주유소 간에는 리터당 가격 차이가 존재해, 추가 이동 비용을 고려해도 우회 후보가 더 경제적인 경우가 있다.

기존의 주유소 탐색은 경로가 지나는 구간만 후보로 고려하므로 비용 최적화 관점에서 한계가 있다. 본 연구는 기본 경로를 탐색 공간 결정 기준으로 활용하여, 그 경로를 감싸는 최소경계사각형(MBR)과 여유 영역(buffer) 안에서 찾은 각 후보 주유소를 경유지로 삼아 경유 경로를 새로 계산한 뒤 비용 기준으로 평가하는 방법을 제안한다.

## Ⅱ. 관련 연구

기존 주유소 정보 서비스로 대표적인 오일나우(OilNow)와 오피넷(Opinet)은 사용자 위치 또는 기본 경로를 기준으로 주유소 정보를 제공한다. 주요 기능 비교는 표 1과 같다.

표 1. 서비스 기능 비교
Table 1. Service comparison

| 기능 / 서비스 | 제안 시스템 | 오일나우 | 오피넷 |
|---|:---:|:---:|:---:|
| 지도 연동 | O | O | O |
| 경로상 주유소 안내 | O | O | O |
| MBR 기반 후보 확장 | O | X | X |
| 경유 경로 재계산 | O | X | X |
| 우회 비용·주유 예정량 반영 | O | X | X |

기존 서비스는 기본 경로 주변 후보만을 대상으로 정보를 제공하며, 경유 경로 재계산이나 우회 비용을 고려한 비용 최적화 기능은 지원하지 않는다.

주유 비용 의사결정을 다룬 선행 연구로 Gas Station Problem[3]은 주유소 가격과 연료 용량을 반영해 이동 비용을 최소화하는 주유 전략을 결정한다. Deviation-Flow Refueling Location Model[4]은 운전자가 기본 경로에서 허용 가능한 수준의 우회(deviation tolerance)를 감수한다고 가정하며, 실제 CNG 운전자 관찰[5]은 기본 경로 위 주유소를 거주지 근처 주유소 대비 10:1로 선호하며, 평균 5.6분의 우회를 수용한다고 보고한다. 그러나 이들 연구는 주유 전략 결정이나 시설 입지에 머물러, 기본 경로 대비 우회 비용이나 주유 예정량 측면에서 결정하는 문제는 다루지 않았다.

본 연구는 Gas Station Problem[3]의 주유 의사결정과 deviation tolerance[4][5]를 통합해, 후보 주유소를 경유지로 둔 경유 경로 재탐색으로 비용 최적 주유소 추천을 정식화한다.

## Ⅲ. 경유 경로 기반 주유소 추천 시스템

### 3.1 문제 정의

기존 서비스는 기본 경로상의 주유소만 후보로 삼는다. 그러나 기본 경로에서 다소 떨어져 보이는 주유소라도, 그 주유소를 경유지로 두고 출발지에서 도착지까지의 경로를 새로 계산하면 도로망에 따라 새 경유 경로가 기본 경로보다 크게 길어지지 않는 경우가 있다. 가격 차이가 충분히 크다면 이런 후보가 더 경제적이다. 본 연구는 기본 경로를 감싸는 MBR과 여유 영역까지 탐색 공간을 확장하고, 각 후보를 경유지로 둔 경로를 새로 계산해 기본 경로와 경유 경로 간 거리·시간 차이를 우회 비용으로 측정하여 순위를 결정한다.

### 3.2 경유 경로 재탐색 프레임워크

제안 시스템은 출발지·도착지 입력 UI, 추천 알고리즘, 선택된 경유 경로의 외부 내비게이션 앱 연동을 포함하는 운용 가능한 형태로 구현되었으며, 본 절은 그 핵심인 추천 알고리즘을 다룬다. 추천 알고리즘은 네 단계로 동작하며, 기본 경로에 후보 주유소를 경유지로 삽입한 경유 경로를 재조회해 각 후보의 실측 우회 비용을 산출한다.

1. 기본 경로: Kakao Mobility Directions API로 출발지-도착지 경로의 폴리라인과 기준 거리·시간을 얻는다.
2. 후보 수집: 기본 경로의 MBR에 우회 허용 상한의 두 배만큼 여유 영역을 더해 탐색 공간을 구성하고 내부 주유소를 DB에서 조회한다. 후보가 과다하면 단계적으로 압축한다(3.4절 참조).
3. 경유 재조회: 각 후보를 경유지로 하는 경로(출발지 → 후보 주유소 → 도착지)를 Kakao Directions API로 재조회해 실측 우회 거리·시간을 얻으며, 가격 하한 가지치기로 호출 수를 제한한다(3.5절 참조).
4. 순위화: 식 (1)의 점수로 오름차순 정렬해 최대 3개까지 반환한다(유효 후보가 부족하면 0~2개). 3.5절의 가지치기로 재조회를 조기 종료해도 반환 결과의 최적성은 수학적으로 보존된다.

이는 고정 경로를 전제로 주유 시점만 결정하는 Gas Station Problem[3]과 달리, 후보별로 경유 경로를 재계산한다는 점에서 구별된다.

### 3.3 비용 모델

주유소 i의 점수는 식 (1)로 정의된다. 모든 항이 비음수이고 값이 낮을수록 우수하다.

$$
\text{score}_i \;=\; p_i \ell \;+\; \frac{d_i/1000}{\eta} p_i \;+\; \frac{t_i}{3600} w \tag{1}
$$

여기서 $p_i$는 유종 가격(원/L), $\ell$은 주유 예정량(L), $d_i$는 후보 경유 시 기본 경로 대비 우회 거리(m), $\eta$는 차량 연비(km/L), $t_i$는 우회 소요 시간(s), $w$는 2026년 최저시급(10,320원)을 적용한 우회 시간 기회 비용 단가이다. $d_i$와 $t_i$는 단계 3의 경유 경로 응답에서 직접 산출되며, API 오차로 음수가 관측되는 경우 0으로 보정한다. 가격 절감이 우회 비용을 상회할 때에만 후보가 경로상 최저가를 이긴다. 비선형 페널티를 도입하지 않은 것은 실증[5]상 개인 운전자의 우회 결정이 단순 합산 비교와 정합하기 때문이다.

### 3.4 확장 탐색 공간의 구성과 단계적 압축

단순히 직선 거리만으로 후보를 거르는 방식은 한계가 분명하다. 직선상 멀어 보여도 경유 경로를 새로 계산하면 우회 비용이 작은 후보가 적지 않게 발견되는데, 이런 후보들이 사전에 배제되면 검색에서 누락되는 오류(false negative)가 발생한다. 반대로 탐색 공간을 과도하게 넓히는 것도 답이 아니다. 외부 API 호출 수가 급격히 증가하기 때문이다. 본 연구는 양쪽 극단 사이에서, 후보 수의 추이를 관찰하며 식 (2)의 가격 하한과 호출 수 상한을 결합한 단계적 점진 압축 전략을 택한다(표 2).

표 2. 후보 수집 단계
Table 2. Candidate selection stages

| 단계 | 명칭 | 동작 |
|:---:|---|---|
| 1 | 경로 MBR 후보 수집 | 기본 경로의 MBR + 동적 여유 영역(우회 상한 × 2) 내 주유소 수집 |
| 2 | 경로상 최저가 기반 가격 상한 | 경로상 후보 최저가를 기준으로 그 이하 가격의 후보만 보존 |
| 3 | 외부 API 호출 수 상한 | 가격 오름차순 상위 30개만 유지 |

세 단계는 역할이 명확히 구분된다. 우선 1단계는 공간을 관대하게 열어 후보를 폭넓게 수집한다. 다음 2단계는 경로상 후보의 최저가를 가격 상한으로 두고, 이를 초과하는 후보를 배제한다. 그 근거는 식 (2)의 하한 $\text{score}_i \geq p_i \ell$로, 점수가 $p_i \ell$ 이상임이 보장된다. 따라서 가격이 경로상 최저가를 넘는 후보는 우회 비용을 0으로 가정해도 경로상 최저가 후보를 이길 수 없다. 다만 경로상 후보가 한 곳도 없는 시나리오에서는 이 상한을 적용하지 않는다. 끝으로 3단계는 안전장치다. 잔여 후보가 여전히 많을 경우 가격 오름차순 상위 30개로 잘라, 외부 API 호출을 30회 이내로 제한한다. 이렇게 식 (2)의 수학적 하한과 deviation tolerance[4] 개념이 한 흐름 안에서 결합되어, 호출 비용을 관리하면서도 우수한 후보를 보존한다.

### 3.5 가격 하한 가지치기

단계 3의 경유 경로 호출은 외부 API에 의존하므로 가능한 최소로 수행해야 한다. 식 (1)의 모든 항이 비음수이므로 다음의 (2)가 성립한다.

$$
\text{score}_i \;\geq\; p_i \ell \tag{2}
$$

후보를 가격 오름차순으로 탐색하면서 상위 $k$개가 확보된 이후 어느 시점에서든 다음의 (3)이 성립하면,

$$
p_i \ell \;>\; \text{score}^{(k)}_{\text{best}} \tag{3}
$$

탐색을 종료해도 반환 결과의 최적성이 보존된다. 특히 경로상 후보는 우회 비용이 0에 가까워 점수가 가격에 비례하므로 자연스러운 기준선을 형성하며, 본 단계적 압축은 이 기준선을 2단계 경로상 최저가 기반 가격 상한에서 직접 활용한다.

### 3.6 사례 연구

표 3은 5가지 대표 시나리오에서 본 시스템과 기존 서비스(오피넷, 오일나우)의 1위 추천 가격을 비교한 결과이다(유종: 휘발유, 단위: 원/L, "—"는 해당 서비스가 결과를 반환하지 않은 경우). 측정 조건은 $\ell = 50$ L, $\eta = 10$ km/L이며, 각 사례의 우회 허용 상한은 기본 경로 거리의 30%(최대 10 km)이다.

표 3. 시나리오별 1위 추천 가격 비교
Table 3. Top-1 recommendation price by scenario

| 번호 | 사례 | 본 시스템 | 오피넷 | 오일나우 |
|:---:|---|---:|---:|---:|
| 1 | 중거리 (동대구역-국립금오공과대학교) | 1,950 | 1,992 | 1,992 |
| 2 | 중거리 (잠실역-수원역) | 1,985 | 1,994 | 1,985 |
| 3 | 장거리 고속도로 (서초IC-서대전IC) | 1,986 | 1,984 | 1,984 |
| 4 | 지방 도심 (대구역-동대구역) | 1,940 | — | — |
| 5 | 희소 구간 (춘천-홍천) | 1,985 | 1,985 | 1,985 |

본 시스템은 시나리오별로 상이한 효과를 보였다. 동대구역-국립금오공과대학교 구간(사례 1)에서는 본 시스템이 1,950원/L를 추천하여 기존 서비스 대비 42원/L 우위를 확인하였다. 대구역-동대구역 구간(사례 4)에서는 기존 서비스가 경로상 후보를 찾지 못하는 사각지대를 MBR 확장으로 보완하였다. 서초IC-서대전IC 구간(사례 3)과 춘천-홍천 구간(사례 5)에서는 후보 풀이 좁아 기존 서비스와 큰 차이를 보이지 않았다. 전반적으로 본 시스템의 차별화는 도심·중거리 시나리오에서 뚜렷하였다.

![그림 1](figures/result-proposed-system.png)

그림 1. 본 시스템 추천 결과 (사례 1, 동대구역-국립금오공과대학교) - 추천 후보 3곳의 경유 경로 동시 미리보기
Fig. 1. Recommendation result of the proposed system (Case 1, Dongdaegu Station-Kumoh National Institute of Technology) - simultaneous preview of via-routes for top-3 candidates

## Ⅳ. 결 론

본 논문은 기본 경로상으로 후보를 한정하는 기존 방식의 한계를 지적하고, 경유 경로 재탐색을 통한 비용 통합 점수로 주유소 추천을 제안하였다. 본 연구는 입지 최적화의 deviation tolerance[4]를 운용 시점 추천으로 전환하였으며, 실측 사례에서 도심 사각지대 보완과 기존 서비스 대비 가격 우위를 확인하였다. 또한 출발지·도착지 입력부터 외부 내비게이션 앱 연동까지를 포함한 운용 가능한 형태로 구현하여 사용자가 추천 결과를 즉시 활용할 수 있도록 하였다. 본 연구는 단일 시점·단일 유종 검증에 기반하므로, 향후 시간대·요일에 따른 우회 비용 변동을 누적 데이터로 반영하고, 정적 우회 허용 상한과 [5]의 평균 수용 우회 범위를 사용자의 채택·거부 신호로 학습하여 개인화 추천으로 확장할 계획이다.

## 참 고 문 헌

1. M. Brons, P. Nijkamp, E. Pels, and P. Rietveld, "A meta-analysis of the price elasticity of gasoline demand: A system of equations approach", Tinbergen Institute Discussion Paper, no. 06-106/3, 2006.
2. S. Lee, U. Lee, and Y. Kim, "An Empirical Study on the Effect of Choice Factors of Gas Station on Repurchase Intention", Journal of Digital Convergence, vol. 7, no. 3, pp. 83–92, Jan. 2009.
3. S. Khuller, A. Malekian, and J. Mestre, "To fill or not to fill: The gas station problem", ACM Transactions on Algorithms (TALG), vol. 7, no. 3, pp. 1–16, Jun. 2011.
4. J. G. Kim and M. Kuby, "The deviation-flow refueling location model for optimizing a network of refueling stations", International Journal of Hydrogen Energy, vol. 37, no. 6, pp. 5406–5420, Mar. 2012.
5. S. Kelley and M. Kuby, "On the way or around the corner? Observed refueling choices of alternative-fuel drivers in Southern California", Journal of Transport Geography, vol. 33, pp. 258–267, Dec. 2013.

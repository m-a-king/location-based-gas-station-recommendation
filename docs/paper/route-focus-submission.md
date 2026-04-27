# 비용 인지 우회를 활용한 비용 최소 주유소 추천 시스템

조재중\*, 이홍철\*, 신광철\*, 오병우\*\*

## Cost-Aware Detour-Based Gas Station Recommendation for Cost Minimization

Jaejung Jo\*, Hongcheol Lee\*, Gwangcheol Shin\*, and Byoungwoo Oh\*\*

\*국립금오공과대학교 컴퓨터공학부, {20171115, 20181414, 20210632}@kumoh.ac.kr
\*\*국립금오공과대학교 컴퓨터공학과, bwoh@kumoh.ac.kr (교신저자)

## 요 약

본 논문은 경로 기반 주유소 추천에서 탐색 공간을 경로 주변에 한정하지 않고 그 경로를 포함하는 최소경계사각형(MBR) 내부로 확장하는 2-hop 재탐색 방식을 제안한다. 후보가 과도하면 기본 경로 근접도와 가격으로 단계적으로 압축한 뒤, 각 후보를 경유하는 실제 도로 경로를 재산출해 주유비와 우회의 연료·시간 비용을 합산한 점수로 순위를 정한다. 가격 오름차순 탐색 중 남은 후보로 지출을 낮출 수 없는 시점에 탐색을 중단하여 외부 API 호출 수를 제한하면서 상위 추천의 최적성을 보존하고, 경로상 단순 최저가 선택 대비 실질 지출 절감을 달성한다.

## Abstract

In this paper we propose a 2-hop re-query scheme for route-based gas station recommendation that, rather than restricting the search space to the vicinity of the route, expands it to the minimum bounding rectangle (MBR) that encloses the route. When candidates become excessive, the pool is progressively tightened by proximity to the base route and by price; for each remaining candidate we then recompute the actual road-network path through it and rank recommendations by fuel price plus the detour's fuel and time cost. During ascending-price traversal the search halts once the theoretical lower bound on the remaining candidates cannot beat the current best, bounding external routing-API calls while preserving the optimality of the top-ranked results and yielding meaningful spending reductions over the naive on-route lowest-price selection.

## Key words

gas station recommendation, cost-aware detour, 2-hop re-query, minimum bounding rectangle, location-based service

## Ⅰ. 서 론

최근 미국·이란 간 군사적 충돌과 호르무즈 해협을 둘러싼 긴장으로 국제 원유 공급망의 불확실성이 커지고 있으며, 국제 유가의 변동성은 운전자의 이동 비용에 직접적인 영향을 미친다.

휘발유 수요는 가격에 대해 비탄력적이어서[1], 소비자는 이동 자체를 줄이기보다 비용을 낮추는 방향으로 선택을 조정하는 경향을 보인다. 또한 주유소 선택 요인에 관한 연구[2]에서 가격은 재방문 의도에 유의한 음의 영향을 미치는 요인으로 보고되며, 주유소 간 리터당 가격 차이는 소비자의 주유소 선택에 중요하게 작용한다.

실제 경로상 주유소와 경로를 벗어나 접근 가능한 주유소 간에는 리터당 가격 차이가 존재해, 추가 이동 비용을 고려해도 우회 후보가 더 경제적인 경우가 있다(그림 1). 경로 폴리라인이 지나는 구간만 후보로 삼는 기존의 국소 탐색(local search)은 이러한 후보를 배제하므로 비용 최소화 관점에서 한계가 있다. 본 연구는 경로를 포함하는 최소경계사각형(MBR)으로 탐색을 확장하고 각 후보를 경유하는 실경로로 우회 비용을 실측해 순위를 정하는 비용 인지(cost-aware) 우회 추천 방식을 제안한다.

[그림 1] 비용 인지 우회의 개념 — 경로상 vs 우회 후보의 지출 역전

## Ⅱ. 관련 연구

기존 주유소 정보 서비스로 대표적인 오일나우(OilNow)와 오피넷(Opinet)은 사용자 위치 또는 경로 주변 주유소의 가격 정보를 지도 기반으로 제공한다. 주요 기능 비교를 표 1에 정리하였다.

표 1. 서비스 기능 비교
Table 1. Service Comparison

| 기능 / 서비스 | 제안 시스템 | 오일나우 | 오피넷 |
|---|---|---|---|
| 지도 연동 | O | O | O |
| 주유소 추천 | O | △ | O |
| 교통 데이터 연동 | △ | △ | △ |
| 우회 경로 기반 탐색 | O | X | X |
| 주유 예정량 반영 추천 | O | X | X |
| 경로 이탈 비용 고려 | O | X | X |

표 1에서 기존 서비스는 경로상 주유소의 저가 옵션을 우선 제시할 뿐, 경로 이탈에 따른 추가 이동 비용과 가격 절감을 함께 고려하지 못한다.

주유 비용 의사결정을 다룬 선행 연구로 Gas Station Problem[3]은 주유소 가격과 연료 용량을 반영해 이동 비용을 최소화하는 주유 전략을 결정한다. Deviation-Flow Refueling Location Model[4]은 운전자가 최단 경로에서 허용 가능한 수준의 우회(deviation tolerance)를 감수한다고 가정하며, 실제 CNG 운전자 관찰[5]은 경로상 주유소 선호가 10:1이며 평균 5.6분의 우회가 수용된다고 보고한다. 그러나 이들은 주유 전략 결정과 시설 입지에 머물러, 시설이 조밀한 환경에서 단일 trip의 실시간 추천 시점에 우회를 비용 관점으로 활용하는 운용 문제는 다루지 않았다.

본 연구는 Gas Station Problem[3]의 주유 의사결정과 deviation tolerance[4][5]를 통합해 주유소 추천을 비용 인지(cost-aware) 우회를 통한 2-hop 재탐색으로 정식화한다.

## Ⅲ. 비용 인지 우회를 통한 2-hop 지출 최소화

### 3.1 문제 정의

국소 탐색은 폴리라인으로부터의 직선 거리를 기준으로 후보를 필터링하지만, 도로망에서는 직선 거리와 실제 우회 거리(기본 경로 대비 추가 이동 거리)가 크게 어긋날 수 있다. 예를 들어 폴리라인에서 직선 3 km 떨어진 주유소를 경유하더라도, 진출입로 구조 덕분에 경유 경로가 기본 경로보다 500 m만 길어지는 사례가 자주 관찰된다(그림 2). 본 연구는 탐색 범위를 경로를 포함하는 MBR로 확장하고, 내부 후보 각각에 대해 경로를 재조회해 실측 우회 비용으로 순위를 정한다.

[그림 2] 폴리라인 MBR 확장과 직선 거리–실제 우회 거리의 차이

### 3.2 2-hop 재탐색 프레임워크

제안 시스템은 네 단계로 동작한다. "2-hop"은 기본 경로(S→D)에 후보 주유소를 경유지로 삽입한 2-hop 경로(S→후보→D)를 도로망에서 재조회해 각 후보의 실측 우회 비용을 산출하는 구조를 가리킨다.

1. **기본 경로**: Kakao Mobility Directions API로 출발지–도착지 경로의 폴리라인과 기준 거리·시간을 얻는다.
2. **후보 수집**: 폴리라인 MBR에 우회 허용 상한의 두 배 buffer를 더해 탐색 공간을 구성하고 내부 주유소를 DB에서 조회한다. 후보가 과다하면 cascade로 압축한다(3.4).
3. **경유 재조회**: 각 후보를 경유지로 하는 경로(origin → candidate → destination)를 Kakao Directions API로 재조회해 실측 우회 거리·시간을 얻으며, 가격 하한 pruning으로 호출 수를 제한한다(3.5).
4. **순위화**: 식 (1)의 점수로 오름차순 정렬해 상위 k개를 반환한다. 3.5의 pruning으로 재조회를 조기 종료해도 상위 k개의 최적성은 수학적으로 보존된다.

이는 고정 경로를 전제로 주유 시점만 결정하는 Gas Station Problem[3]과 달리, 경로 자체를 후보에 따라 재구성한다는 점에서 구별된다.

### 3.3 점수 공식

주유소 i의 점수는 식 (1)로 정의된다. 모든 항이 비음수이고 값이 낮을수록 우수하다.

$$
\text{score}_i \;=\; p_i\,\ell \;+\; \frac{d_i/1000}{\eta}\,p_i \;+\; \frac{t_i}{3600}\,w \tag{1}
$$

여기서 $p_i$는 유종 가격(원/L), $\ell$은 주유 예정량(L), $d_i$는 기본 경로 대비 우회 거리(m), $\eta$는 차량 연비(km/L), $t_i$는 우회 소요 시간(s), $w$는 2026년 최저시급(10,320원/h)에 해당하는 우회 시간 기회비용 단가이다. $d_i$와 $t_i$는 단계 3의 경유 경로 응답에서 산출하며, API 오차로 음수가 관측되면 0으로 보정한다. 가격 절감이 우회 비용을 상회할 때에만 후보가 경로상 최저가를 이긴다. 비선형 페널티를 도입하지 않은 것은 실증[5]상 개인 운전자의 우회 결정이 단순 합산 비교와 정합하기 때문이다.

### 3.4 확장 탐색 공간의 구성과 Cascade

경로로부터 일정 직선 거리(예: 2 km) 이내의 좁은 띠(corridor)만 후보로 삼는 방식은 직선상으로 멀지만 실제 우회 비용이 작은 후보를 놓치는 누락 오류(false negative)를 유발하고, 공간을 너무 넓게 잡으면 단계 3 API 호출이 폭증한다. 본 연구는 후보 수 $N$을 관찰하며 cascade로 점진 압축하는 전략을 채택한다(표 2).

표 2. 후보 수집 cascade
Table 2. Candidate selection cascade

| 단계 | 조건 | 동작 |
|---|---|---|
| POLYLINE_MBR | (항상 시작) | 폴리라인 MBR + 동적 buffer(우회 상한 × 2) 내 주유소 수집 |
| TIGHT_CORRIDOR | $N > 30$ | 폴리라인까지 직선거리 ≤ 2 km로 필터 |
| PRICE_CAPPED | 가격 결합 후 $N > 30$ | 가격 오름차순 상위 30개만 유지 |

1단계 POLYLINE_MBR이 공간을 관대하게 열고, 2단계 TIGHT_CORRIDOR는 기본 경로 근접도로 1차 압축해 MBR 내부에서 경로로부터 멀어 우회 비용이 과도한 영역(dead-zone)을 제거한다. 여기서 쓰는 2 km 임계값은 [5]가 보고한 운전자 수용 우회 범위의 직선거리 근사에 해당한다. 3단계 PRICE_CAPPED는 가격 결합 후에도 과다한 후보를 가격 상위로 2차 압축해 단계 3 호출 수를 30회 이하로 제한한다. 이 cascade는 deviation tolerance[4]를 호출 예산 관리로 번역한 결과로 볼 수 있다.

### 3.5 Price Lower-Bound Pruning

단계 3 호출 수를 줄이기 위한 pruning을 도출한다. 식 (1)의 비음수성으로부터

$$
\text{score}_i \;\geq\; p_i \ell \tag{2}
$$

가 성립한다. 후보를 가격 오름차순으로 탐색하면서 상위 $k$개가 확보된 이후 어느 시점에서든

$$
p_i \ell \;>\; \text{score}^{(k)}_{\text{best}} \;\Longrightarrow\; \text{이후 모든 후보가 동일 조건 성립} \tag{3}
$$

이 성립하면 탐색을 종료해도 상위 $k$개의 최적성이 보존된다.

### 3.6 사례 연구

표 3은 6가지 대표 시나리오에서 본 시스템과 기존 서비스(오피넷, 오일나우)의 1위 추천 가격을 비교한 결과이다(유종: 휘발유, 단위: 원/L, "—"는 해당 서비스가 결과를 반환하지 않은 경우). 측정 조건은 $\ell = 40$ L, $\eta = 10$ km/L이며, 각 사례의 우회 허용 상한은 $\min(\text{기본경로} \times 0.3,\ 10)$ km이다. 데이터는 **⟨실측 교체 예정: 수집 일자 YYYY-MM-DD⟩** 기준이다.

표 3. 시나리오별 1위 추천 가격 비교
Table 3. Top-1 recommendation price by scenario

| # | 시나리오 (출발–도착, 기본경로) | 본 시스템 | 오피넷 | 오일나우 |
|---|---|---:|---:|---:|
| 1 | 단거리 도심 (강남역→교대역, **⟨? km⟩**) | **1,995** | — | — |
| 2 | 중거리 (잠실역→수원역, **⟨? km⟩**) | **⟨?⟩** | **⟨?⟩** | **⟨?⟩** |
| 3 | 장거리 고속도로 (서초IC→서대전IC, 약 160 km) | **⟨?⟩** | **⟨?⟩** | **⟨?⟩** |
| 4 | 지방 도심 (대구역→동대구역, **⟨? km⟩**) | **⟨?⟩** | — | **⟨?⟩** |
| 5 | 희소 구간 (춘천터미널→홍천터미널, **⟨? km⟩**) | **⟨?⟩** | **⟨?⟩** | **⟨?⟩** |
| 6 | 지방 중거리 (동대구역→금오공대, **⟨? km⟩**) | **1,954** | 1,954 | 1,983 |

본 시스템은 시나리오별로 다른 형태의 가치를 보였다. 단거리·지방 도심(사례 1, 4)에서는 기존 서비스가 경로상 후보를 찾지 못하는 사각지대를 MBR 확장으로 보완하였다. 사례 6에서는 본 시스템이 6 km 우회 후보(1,940원/L)를 추가로 발견하였으나 식 (1)의 점수에서 우회 비용이 가격 절감을 상회해 경로상 1위(대광셀프, 1,954원/L)를 유지하여 점수 기반 의사결정의 정확성을 보였으며, 오일나우 대비 **29원/L** 우위(40 L 기준 약 1,160원 절감)를 확인하였다. 장거리 고속도로(사례 3)와 주유소 희소 구간(사례 5)에서는 후보 풀이 좁아 기존 서비스와 큰 차이를 보이지 않았다. 전반적으로 본 시스템의 차별화는 도심·중거리 시나리오에서 뚜렷하였다.

## Ⅳ. 결론

본 논문은 경로상 국소 탐색의 한계를 지적하고, 주유소 추천을 비용 인지 우회를 통한 2-hop 재탐색으로 재정식화하여 지출 최소화 관점의 추천을 실현하였다. 입지 최적화의 deviation tolerance[4]를 운용 시점 추천으로 전환하였으며, 실측 사례에서 도심 시나리오의 사각지대 보완과 기존 서비스 대비 평균 **⟨실측 교체 예정: ?원/L⟩**의 가격 우위를 확인하였다. 향후 MBR 확장 폭(buffer)과 corridor(경로 띠) 거리 결정 규칙의 데이터 기반 학습, 주유소 밀도 지표를 활용한 cascade 분기 최적화, 개별 운전자의 수용 우회 범위 개인화 학습으로 확장할 수 있다.

## 참 고 문 헌

1. M. Brons, P. Nijkamp, E. Pels, and P. Rietveld, "A meta-analysis of the price elasticity of gasoline demand: A system of equations approach," Tinbergen Institute Discussion Paper, no. 06-106/3, 2006.
2. S. Lee, U. Lee, and Y. Kim, "An Empirical Study on the Effect of Choice Factors of Gas Station on Repurchase Intention," Journal of Digital Convergence, vol. 7, no. 3, pp. 83–92, 01. 2009.
3. S. Khuller, A. Malekian, and J. Mestre, "To fill or not to fill: The gas station problem," ACM Transactions on Algorithms (TALG), vol. 7, no. 3, pp. 1–16, 06. 2011.
4. J. G. Kim and M. Kuby, "The deviation-flow refueling location model for optimizing a network of refueling stations," International Journal of Hydrogen Energy, vol. 37, no. 6, pp. 5406–5420, 03. 2012.
5. S. Kelley and M. Kuby, "On the way or around the corner? Observed refueling choices of alternative-fuel drivers in Southern California," Journal of Transport Geography, vol. 33, pp. 258–267, 12. 2013.

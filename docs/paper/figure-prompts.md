# 논문 그림 생성 프롬프트

본 논문 제출본(`route-focus-submission.md`)에 삽입할 그림 이미지의 생성용 프롬프트 모음. DALL·E / Midjourney / Stable Diffusion 등에서 사용.

---

## [그림 1] 비용 인지 우회의 개념 — 경로상 vs 우회 후보의 지출 역전

**본문 위치**: Ⅰ. 서론 중단
**권장 포맷**: 16:9, 600 px 폭

### 구성 (한국어 레이아웃 설명)

- 출발지(●) — 도착지(▲)를 파란 실선 경로로 연결
- ON_ROUTE 주유소: 경로 위 빨강 원, 가격 1,650원
- OFF_ROUTE 주유소: 경로에서 소폭(≈2 km) 이탈, 초록 원, 가격 1,500원
- 각 후보 옆 말풍선: "지출 66,033원 / 60,600원"
- 하단 화살표: "OFF_ROUTE가 지출 5,400원 낮음"

### 영문 생성 프롬프트

```
Clean minimal infographic map, top-down view, blue road from
origin (filled circle, labeled '출발') to destination (triangle,
labeled '도착'). Red gas pump icon directly on the road labeled
'1,650원'. Green gas pump icon slightly off the route (2km
detour) labeled '1,500원'. Two callout boxes showing total cost
'66,033원' (red) and '60,600원' (green). Arrow between them with
text 'save 5,400원'. Flat design, Korean-friendly labels, white
background, soft pastel palette.
```

---

## [그림 2] 폴리라인 MBR 확장과 직선 거리–실제 우회 거리의 차이

**본문 위치**: Ⅲ. 3.1 문제 정의 직하 (논문의 독창적 관찰 "3 km vs 500 m"을 시각화)
**권장 포맷**: 4:3 또는 16:9, 700 px 폭

### 구성 (한국어 레이아웃 설명)

```
지도 위 배경
┌──────────────────────────────────────────┐
│   ┌─ 점선 직사각형 (폴리라인 MBR + buffer) ─┐     │
│   │                                          │   │
│   │   S ●━━━━━━━━━━━━━━━━━━━━━━━━━━━━● D     │   │  ← 폴리라인 경로
│   │         ↑ (경로상)                       │   │
│   │         ● P1  1,500원/L                  │   │
│   │                                          │   │
│   │   ● P2  1,350원/L                        │   │
│   │   ╲                                      │   │
│   │    ╲ 직선 3 km (점선)                    │   │
│   │     ╲↳ 실제 진입 경로 500 m 우회 ──→     │   │
│   │                                          │   │
│   └──────────────────────────────────────────┘   │
│       ● P3 (MBR 밖, 배제됨)                      │
└──────────────────────────────────────────────────┘

하단 주석: "직선 거리(점선) ≠ 도로망 우회 거리(실선)"
```

핵심 강조점:
- **P2**가 주인공. 직선 3 km 떨어져 보이지만 실제 진입은 500 m.
- 점선(직선 거리) vs 실선(실제 도로 경로)의 대비로 "고정 corridor 방식의 false negative" 문제를 시각화.
- 점선 직사각형 = MBR + buffer 범위 (본 연구가 확장한 탐색 공간).

### 영문 생성 프롬프트

```
Clean minimal academic infographic, top-down schematic map view.
Blue curved road from origin (filled circle, 'S') to destination
(triangle, 'D'). A dashed rectangle enclosing the route with some
margin — label it 'Polyline MBR + buffer'. Three gas station icons:
(1) 'P1' right on the road labeled '1,500원'; (2) 'P2' inside the
rectangle but 3 km in straight-line distance from the road — draw
a dashed straight line showing '3 km' from road to P2, and a
curved solid arrow showing the actual access road of only '500 m';
(3) 'P3' outside the rectangle marked 'excluded'. Subtle grid
background suggesting a map. Flat design, Korean-friendly labels,
white background, single blue accent, academic paper style.
```

---

## 주의

- 그림 3, 4, 5는 부록 A 분량 조정 결정에 따라 제외됨
- 본 프롬프트를 기반으로 이미지 생성 후 hwp 본문에 `[그림 N] 캡션` 자리에 삽입
- 이전 그림 2(시스템 처리 흐름)는 Ⅲ.2의 4단계 번호 목록과 정보 중복이 커 본 논문에서 제외됨. 위 MBR 개념도로 교체하여 Ⅲ.1 독창적 관찰의 시각적 방어력을 강화함

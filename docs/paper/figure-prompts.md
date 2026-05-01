# 논문 그림 생성 프롬프트

본 논문 제출본(`route-focus-submission.md`)에 삽입할 그림 이미지의 생성용 프롬프트 모음. DALL·E / Midjourney / Stable Diffusion 등에서 사용.

---

## [그림 1] 폴리라인 MBR 확장과 직선 거리–실제 우회 거리의 차이

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

- 이전 [그림 1] "비용 인지 우회의 개념" 캡션은 새 제목·메시지 정합화 과정에서 본문 자조어가 제거되며 함께 삭제됨. 본 논문은 그림 1개로 운영.
- 본 프롬프트를 기반으로 이미지 생성 후 hwp 본문에 `[그림 1] 캡션` 자리에 삽입.

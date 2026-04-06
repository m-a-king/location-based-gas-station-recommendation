# 참고 문헌 매핑

본 문서는 프로젝트에서 구현한 각 알고리즘·수식이
어떤 참고 문헌의 어떤 부분에 근거하는지를 대응시킨 것이다.

---

## 1. Haversine 공식 — 두 좌표 사이의 거리 구하기

### 참고 문헌

> Sinnott, R.W. (1984). "Virtues of the Haversine."
> *Sky and Telescope*, 68(2), 159.

### 쉬운 설명

**문제**: 서울(37.5°N, 127.0°E)에서 부산(35.1°N, 129.0°E)까지 **직선 거리**가 몇 km인지 알고 싶다.

그런데 지구는 평면이 아니라 **공**이다. 평면 위의 두 점 거리는 피타고라스 정리로 구할 수 있지만, 공 위에서는 그게 안 된다. 공 표면을 따라 가는 **호(arc)의 길이**를 구해야 한다.

Sinnott(1984)는 이 문제를 풀기 위해 삼각함수 `sin`, `cos`을 조합한 공식을 제안했다.

### 공식

```
① h = sin²(위도차/2) + cos(위도A) × cos(위도B) × sin²(경도차/2)
② 거리 = 2 × 지구반지름 × arcsin(√h)
```

쉽게 풀어서:
- **① 단계**: 두 점의 위도 차이와 경도 차이를 삼각함수에 넣어서 `h`라는 중간값을 만든다.
- **② 단계**: `h`에 루트를 씌우고, `arcsin`(역삼각함수)을 취하고, 지구 반지름(6,371km)을 곱하면 거리(미터)가 나온다.

### 대응 코드

**파일**: `src/main/kotlin/com/kumoh/lbs/geo/GeoUtils.kt` (12–26행)

```kotlin
// ① 중간값 h 계산
val sinLat = sin(dLat / 2)                        // 위도차의 반을 sin
val sinLon = sin(dLon / 2)                        // 경도차의 반을 sin
val h = sinLat * sinLat                            // sin²(위도차/2)
      + cos(lat1) * cos(lat2) * sinLon * sinLon   // + cos × cos × sin²(경도차/2)

// ② 거리 계산
return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))    // 2 × R × arcsin(√h)
```

**코드와 공식이 한 줄 한 줄 일치**한다. `h` 변수가 공식의 ①에 해당하고, `return` 문이 ②에 해당한다.

### 왜 이 공식을 썼나

지구를 완벽한 공으로 가정하면 최대 약 0.3%의 오차가 생긴다. 하지만 주유소 추천에서 "1km 거리"가 "1.003km"로 계산되어도 결과에 영향을 주지 않으므로 충분하다.

---

## 2. 좌표 변환 — KATEC 좌표를 GPS 좌표(WGS84)로 바꾸기

### 참고 문헌

> Snyder, J.P. (1987). *Map Projections: A Working Manual.*
> U.S. Geological Survey Professional Paper 1395.
> Chapter 8: "Transverse Mercator Projection", pp. 48–64.

### 쉬운 설명

**문제**: OPINET(주유소 가격 API)은 주유소 위치를 `(309048, 540530)` 같은 숫자로 준다(KATEC 좌표). 그런데 카카오맵이나 GPS에서는 `(37.5665, 126.9780)` 같은 위도·경도(WGS84)를 쓴다. 이 두 체계를 **서로 변환**해야 한다.

**비유**: 같은 서울시청 위치를 표현하는 방식이 두 개 있다고 생각하면 된다.

| 좌표 체계 | 서울시청 위치 | 단위 | 쓰는 곳 |
|---|---|---|---|
| KATEC | (309048, 540530) | 미터 | OPINET API |
| WGS84 | (37.5665, 126.9780) | 위도·경도(도) | GPS, 카카오맵 |

둘 다 **같은 장소**를 가리키지만, 표현하는 숫자 체계가 다르다. 마치 같은 온도를 섭씨(°C)와 화씨(°F)로 표현하는 것과 비슷하다.

### 변환 원리 (횡메르카토르 투영)

Snyder(1987)가 정리한 **횡메르카토르 투영**이라는 수학적 방법을 사용한다. 핵심 아이디어는:

1. 지구는 둥근 공(타원체)이다
2. 이 공 위의 위치(위도·경도)를 **평평한 종이 위의 좌표(x, y 미터)**로 옮기는 것이 "투영"
3. KATEC은 한국에 최적화된 투영 방식으로, **기준점과 설정값**이 정해져 있다

그 설정값이 코드의 이 부분이다:

### 대응 코드

**파일**: `src/main/kotlin/com/kumoh/lbs/geo/CoordinateConverter.kt` (18–21행)

```kotlin
"+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000 +ellps=GRS80 +units=m +no_defs"
```

각 설정값의 의미:

| 설정 | 값 | 쉬운 의미 |
|---|---|---|
| `+proj=tmerc` | 횡메르카토르 | 투영 방식의 이름 |
| `+lat_0=38` | 북위 38° | 지도의 기준 위도 (한반도 중앙 부근) |
| `+lon_0=128` | 동경 128° | 지도의 기준 경도 (한반도 중앙 부근) |
| `+k=0.9999` | 0.9999 | 축척 보정값 (왜곡을 골고루 퍼뜨리는 장치) |
| `+x_0=400000` | 40만 | x좌표에 40만을 더함 (음수가 안 나오게) |
| `+y_0=600000` | 60만 | y좌표에 60만을 더함 (음수가 안 나오게) |
| `+ellps=GRS80` | GRS80 | 지구 모양 모델 (한국 공식 채택) |

이 설정값들은 국내 공공기관의 KATEC 좌표계 정의를 따른다. 횡메르카토르 투영 자체의 수학적 원리는 Snyder(1987) Chapter 8에 정리되어 있다.

### 왜 직접 수식을 구현하지 않았나

변환 수식 자체가 매우 복잡하다(Snyder 책 pp. 60–63에 걸쳐 있음). 그래서 이 수식을 이미 정확하게 구현해 놓은 **Proj4j 라이브러리**를 사용했다.

---

## 3. Proj4j — 좌표 변환 라이브러리

### 참고 문헌

> LocationTech. Proj4J 1.3.0.
> https://github.com/locationtech/proj4j

### 쉬운 설명

Proj4J는 위 2번에서 설명한 좌표 변환을 **대신 계산해주는 Java 라이브러리**이다. 프로젝트에서 사용하는 버전은 `org.locationtech.proj4j:proj4j:1.3.0`이다.

### 대응 코드

**파일**: `src/main/kotlin/com/kumoh/lbs/geo/CoordinateConverter.kt` (28–37행)

```kotlin
// KATEC → WGS84: OPINET 좌표를 GPS 좌표로 변환
fun katecToWgs84(katec: Coordinate.Katec): Coordinate.Wgs84 {
    val dst = ProjCoordinate()
    katecToWgs84Transform.transform(ProjCoordinate(katec.x, katec.y), dst)
    return Coordinate.Wgs84(latitude = dst.y, longitude = dst.x)
}

// WGS84 → KATEC: GPS 좌표를 OPINET 좌표로 변환
fun wgs84ToKatec(wgs84: Coordinate.Wgs84): Coordinate.Katec {
    val dst = ProjCoordinate()
    wgs84ToKatecTransform.transform(ProjCoordinate(wgs84.longitude, wgs84.latitude), dst)
    return Coordinate.Katec(x = dst.x, y = dst.y)
}
```

내부적으로 Snyder(1987)의 횡메르카토르 변환 공식이 실행되지만, 우리 코드에서는 **입력(KATEC)을 넣으면 출력(WGS84)이 나오는** 함수로만 사용한다.

### 정확도 검증

테스트(`CoordinateConverterTest.kt`)에서 서울시청 좌표를 KATEC → WGS84 → KATEC으로 왕복 변환했을 때, 오차가 위도 0.03%, 경도 0.01% 이내임을 확인했다.

---

## 4. 바운딩 박스 — "위도 1도 = 약 111km" 환산과 코사인 보정

### 참고 문헌

> Snyder, J.P. (1987). *Map Projections: A Working Manual.*
> USGS Professional Paper 1395.

### 쉬운 설명

**문제**: 어떤 지점에서 반경 2km 안에 있는 주유소를 DB에서 찾고 싶다. DB에는 위도·경도만 저장되어 있는데, "2km"를 위도·경도 몇 도로 바꿔야 SQL 검색이 가능하다.

**핵심 사실** (Snyder, 1987의 위도·경도 길이 관계를 참고한 근사):

```
위도(남북) 방향: 1도 ≈ 111,320 미터 (어디서나 거의 같음)
경도(동서) 방향: 1도 ≈ 111,320 × cos(위도) 미터 (위도에 따라 달라짐)
```

왜 경도는 위도에 따라 달라질까? 지구본을 떠올려보면:
- **적도** 근처: 경도선 사이 간격이 **넓다** → 경도 1도 = 약 111km
- **북극** 근처: 경도선이 한 점으로 **모인다** → 경도 1도 = 거의 0km
- **한국**(위도 37°): 경도 1도 = 111,320 × cos(37°) ≈ **88,900m** (약 89km)

이 줄어드는 비율이 바로 `cos(위도)`이다.

### 대응 코드

**파일**: `src/main/kotlin/com/kumoh/lbs/geo/BoundingBox.kt` (15–19행)

```kotlin
private const val METERS_PER_LATITUDE_DEGREE = 111_320.0  // 1도 ≈ 111.32km 구면 근사 상수

// 2000미터를 위도 차이(도)로 변환
val latDelta = radiusMeters / METERS_PER_LATITUDE_DEGREE
// 예: 2000 / 111320 ≈ 0.01797° (약 0.018도)

// 2000미터를 경도 차이(도)로 변환 — cos 보정 적용
val lonDelta = radiusMeters / (METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(위도)))
// 예: 2000 / (111320 × cos(37°)) ≈ 0.02249° (약 0.022도)
```

경도 쪽이 위도 쪽보다 **숫자가 더 크다**. 한국에서는 경도 1도의 실제 거리가 위도 1도보다 짧기 때문에, 같은 2km를 표현하려면 경도를 더 넓게 잡아야 하기 때문이다.

### 결과

이렇게 만든 사각형(바운딩 박스) 안에서 DB 검색을 하면, 대략 반경 2km 안의 주유소 후보를 빠르게 걸러낼 수 있다. 이후 Haversine 공식(1번)으로 정확한 거리를 계산해서 진짜 2km 안에 있는 것만 남긴다.

---

## 참고 문헌 목록

| # | 문헌 | 코드에서 사용한 부분 |
|---|---|---|
| 1 | Sinnott, R.W. (1984). "Virtues of the Haversine." *Sky and Telescope*, 68(2), 159. | `GeoUtils.kt` — 두 좌표 사이 거리 계산 공식 |
| 2 | Snyder, J.P. (1987). *Map Projections: A Working Manual.* USGS Professional Paper 1395. | `CoordinateConverter.kt` — TM 투영 일반 공식 (Ch.8) / `BoundingBox.kt` — 위도·경도 길이 관계를 참고한 근사 상수 |
| 3 | LocationTech. Proj4J 1.3.0. https://github.com/locationtech/proj4j | `CoordinateConverter.kt` — 좌표 변환 라이브러리 |

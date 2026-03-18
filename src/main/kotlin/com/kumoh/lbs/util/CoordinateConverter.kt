package com.kumoh.lbs.util

import com.kumoh.lbs.domain.Coordinate
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

/**
 * KATEC ↔ WGS84 좌표 변환기.
 *
 * - KATEC: OPINET API가 사용하는 한국 고유 좌표계 (단위: 미터)
 * - WGS84(EPSG:4326): GPS/지도에서 사용하는 세계 표준 좌표계 (단위: 위도·경도)
 *
 * Proj4j 라이브러리를 사용하며, 변환 파라미터(+proj=tmerc ...)는
 * 국토지리정보원이 정의한 KATEC 투영 정의입니다.
 */
object CoordinateConverter {

    // KATEC 좌표계 정의: 횡메르카토르(tmerc) 투영, 원점 위도 38° 경도 128°
    private val katecCrs = CRSFactory().createFromParameters(
        "KATEC",
        "+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000 +ellps=GRS80 +units=m +no_defs"
    )
    private val wgs84Crs = CRSFactory().createFromName("EPSG:4326")
    private val transformFactory = CoordinateTransformFactory()

    private val katecToWgs84Transform = transformFactory.createTransform(katecCrs, wgs84Crs)
    private val wgs84ToKatecTransform = transformFactory.createTransform(wgs84Crs, katecCrs)

    fun katecToWgs84(katec: Coordinate.Katec): Coordinate.Wgs84 {
        val dst = ProjCoordinate()
        // Proj4j는 (x, y) 순서 = (easting, northing)
        katecToWgs84Transform.transform(ProjCoordinate(katec.x, katec.y), dst)
        // 변환 결과: dst.x = longitude, dst.y = latitude
        return Coordinate.Wgs84(latitude = dst.y, longitude = dst.x)
    }

    fun wgs84ToKatec(wgs84: Coordinate.Wgs84): Coordinate.Katec {
        val dst = ProjCoordinate()
        // Proj4j에 WGS84를 넣을 때도 (x=경도, y=위도) 순서
        wgs84ToKatecTransform.transform(ProjCoordinate(wgs84.longitude, wgs84.latitude), dst)
        return Coordinate.Katec(x = dst.x, y = dst.y)
    }
}

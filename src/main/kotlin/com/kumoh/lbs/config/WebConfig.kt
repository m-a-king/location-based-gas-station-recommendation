package com.kumoh.lbs.config

import com.kumoh.lbs.domain.Coordinate
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(CoordinateArgumentResolver())
    }
}

class CoordinateArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == Coordinate::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Coordinate {
        val katecX = webRequest.getParameter("katecX")?.toDoubleOrNull()
        val katecY = webRequest.getParameter("katecY")?.toDoubleOrNull()
        val latitude = webRequest.getParameter("latitude")?.toDoubleOrNull()
        val longitude = webRequest.getParameter("longitude")?.toDoubleOrNull()

        val hasKatec = katecX != null || katecY != null
        val hasWgs84 = latitude != null || longitude != null

        if (hasKatec && hasWgs84) {
            throw IllegalArgumentException("katecX/katecY와 latitude/longitude를 동시에 사용할 수 없습니다. 한 좌표계만 입력해주세요.")
        }

        if (hasKatec) {
            requireNotNull(katecX) { "katecY만 입력되었습니다. katecX도 함께 입력해주세요." }
            requireNotNull(katecY) { "katecX만 입력되었습니다. katecY도 함께 입력해주세요." }
            return Coordinate.fromKatec(Coordinate.Katec(katecX, katecY))
        }

        if (hasWgs84) {
            requireNotNull(latitude) { "longitude만 입력되었습니다. latitude도 함께 입력해주세요." }
            requireNotNull(longitude) { "latitude만 입력되었습니다. longitude도 함께 입력해주세요." }
            return Coordinate.fromWgs84(Coordinate.Wgs84(latitude, longitude))
        }

        throw IllegalArgumentException("좌표가 필요합니다. katecX/katecY 또는 latitude/longitude를 입력해주세요.")
    }
}

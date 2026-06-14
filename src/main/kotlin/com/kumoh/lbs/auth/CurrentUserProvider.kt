package com.kumoh.lbs.auth

import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * 인증 principal에서 현재 로그인 사용자를 해석한다.
 * OAuth 로그인(OidcUser)·Bearer JWT(Jwt) 모두 subject가 카카오 sub이며, 이를 키로 사용자를 조회한다.
 * 컨트롤러마다 흩어지던 principal 해석을 한 곳으로 모은다.
 */
@Component
class CurrentUserProvider(
    private val userRepository: UserRepository
) {

    fun resolve(principal: Any): User =
        userRepository.findByKakaoSub(kakaoSub(principal))
            ?: throw IllegalStateException("로그인 사용자를 찾을 수 없습니다: ${kakaoSub(principal)}")

    private fun kakaoSub(principal: Any): String = when (principal) {
        is OidcUser -> principal.subject
        is Jwt -> principal.subject
        else -> throw IllegalStateException("지원하지 않는 인증 방식입니다.")
    }
}

package com.kumoh.lbs.auth

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

/**
 * 카카오 OIDC 로그인 시 사용자를 DB에 등록한다.
 * sub로 조회해 없으면 신규 가입(INSERT)하고, 이미 있으면 기존 사용자를 유지한다.
 * 이름·차량 프로필은 로그인 시점이 아니라 별도 입력 API(/users/me)로 채운다.
 */
@Service
class OAuthUserService(
    private val userRepository: UserRepository
) : OidcUserService() {

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = super.loadUser(userRequest)
        registerIfAbsent(oidcUser.subject)
        return oidcUser
    }

    private fun registerIfAbsent(kakaoSub: String) {
        if (userRepository.findByKakaoSub(kakaoSub) == null) {
            userRepository.save(User(kakaoSub = kakaoSub))
        }
    }
}

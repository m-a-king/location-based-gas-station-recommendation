package com.kumoh.lbs.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "사용자 프로필", description = "로그인 사용자의 이름·차량 프로필 입력/조회 API")
@Validated
@RestController
@RequestMapping("/users/me")
class UserController(
    private val userRepository: UserRepository
) {

    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 이름·차량(차종·연료종류·연비)을 조회합니다.")
    @GetMapping
    fun getMyProfile(@AuthenticationPrincipal principal: Any): UserProfileResponse =
        UserProfileResponse.from(findMe(principal))

    @Operation(summary = "내 프로필 입력/수정", description = "이름과 차량(차종·연료종류·연비)을 입력하거나 수정합니다.")
    @PutMapping
    fun updateMyProfile(
        @AuthenticationPrincipal principal: Any,
        @Valid @RequestBody request: UserProfileRequest
    ): UserProfileResponse {
        val user = findMe(principal)
        user.name = request.name
        user.carModel = request.carModel
        user.fuelType = request.fuelType
        user.fuelEfficiency = request.fuelEfficiency
        return UserProfileResponse.from(userRepository.save(user))
    }

    private fun kakaoSub(principal: Any): String = when (principal) {
        is OidcUser -> principal.subject
        is Jwt -> principal.subject
        else -> throw IllegalStateException("지원하지 않는 인증 방식입니다.")
    }

    private fun findMe(principal: Any): User =
        userRepository.findByKakaoSub(kakaoSub(principal))
            ?: throw IllegalStateException("로그인 사용자를 찾을 수 없습니다: ${kakaoSub(principal)}")
}

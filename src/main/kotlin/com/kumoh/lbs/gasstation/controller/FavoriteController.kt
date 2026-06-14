package com.kumoh.lbs.gasstation.controller

import com.kumoh.lbs.auth.CurrentUserProvider
import com.kumoh.lbs.gasstation.service.FavoriteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "주유소 즐겨찾기", description = "로그인 사용자의 주유소 즐겨찾기 추가·삭제·조회 API")
@RestController
@RequestMapping("/users/me/favorites")
class FavoriteController(
    private val favoriteService: FavoriteService,
    private val currentUserProvider: CurrentUserProvider
) {

    @Operation(summary = "즐겨찾기 목록 조회", description = "내가 즐겨찾기한 주유소를 최근 추가순으로, 내 유종 기준 현재 가격과 함께 조회합니다.")
    @GetMapping
    fun getMyFavorites(@AuthenticationPrincipal principal: Any): List<FavoriteResponse> =
        favoriteService.listFavorites(currentUserProvider.resolve(principal))

    @Operation(summary = "즐겨찾기 추가", description = "주유소를 즐겨찾기에 추가합니다. 이미 추가된 주유소면 변화 없이 통과합니다.")
    @PostMapping("/{gasStationId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun addFavorite(
        @AuthenticationPrincipal principal: Any,
        @PathVariable gasStationId: String
    ) {
        favoriteService.addFavorite(currentUserProvider.resolve(principal), gasStationId)
    }

    @Operation(summary = "즐겨찾기 삭제", description = "주유소를 즐겨찾기에서 제거합니다. 즐겨찾기에 없어도 통과합니다.")
    @DeleteMapping("/{gasStationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFavorite(
        @AuthenticationPrincipal principal: Any,
        @PathVariable gasStationId: String
    ) {
        favoriteService.removeFavorite(currentUserProvider.resolve(principal), gasStationId)
    }
}

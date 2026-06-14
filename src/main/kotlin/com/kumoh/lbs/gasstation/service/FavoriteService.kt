package com.kumoh.lbs.gasstation.service

import com.kumoh.lbs.auth.User
import com.kumoh.lbs.auth.UserFavorite
import com.kumoh.lbs.auth.UserFavoriteRepository
import com.kumoh.lbs.gasstation.controller.FavoriteResponse
import com.kumoh.lbs.gasstation.domain.FuelType
import com.kumoh.lbs.gasstation.repository.GasStationPriceRepository
import com.kumoh.lbs.gasstation.repository.GasStationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * 사용자 주유소 즐겨찾기 추가·삭제·조회를 담당한다.
 * 목록 조회 시 즐겨찾기에 사용자 유종 기준 현재 가격을 결합한다(유종 미입력 시 휘발유 기준).
 */
@Service
@Transactional(readOnly = true)
class FavoriteService(
    private val favoriteRepository: UserFavoriteRepository,
    private val gasStationRepository: GasStationRepository,
    private val gasStationPriceRepository: GasStationPriceRepository
) {

    /** 즐겨찾기 추가. 존재하지 않는 주유소면 404, 이미 즐겨찾기한 주유소면 멱등 처리(중복 저장 안 함). */
    @Transactional
    fun addFavorite(user: User, gasStationId: String) {
        if (!gasStationRepository.existsById(gasStationId)) {
            throw NoSuchElementException("주유소를 찾을 수 없습니다: $gasStationId")
        }
        if (favoriteRepository.existsByUserIdAndGasStationId(user.id, gasStationId)) {
            logger.debug { "이미 즐겨찾기한 주유소입니다: userId=${user.id}, station=$gasStationId" }
            return
        }
        favoriteRepository.save(UserFavorite(userId = user.id, gasStationId = gasStationId))
    }

    /** 즐겨찾기 삭제. 즐겨찾기가 없어도 멱등하게 통과한다. */
    @Transactional
    fun removeFavorite(user: User, gasStationId: String) {
        favoriteRepository.deleteByUserIdAndGasStationId(user.id, gasStationId)
    }

    /** 내 즐겨찾기 목록을 최근 추가순으로 가격과 함께 반환한다. */
    fun listFavorites(user: User): List<FavoriteResponse> {
        val favorites = favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(user.id)
        if (favorites.isEmpty()) return emptyList()

        val fuelType = user.fuelType ?: FuelType.GASOLINE
        val stationIds = favorites.map { it.gasStationId }
        val stations = gasStationRepository.findAllById(stationIds).associateBy { it.id }
        val prices = gasStationPriceRepository
            .findAllByIdStationIdInAndIdFuelType(stationIds, fuelType)
            .associate { it.id.stationId to it.price }

        // 배치 교체 등으로 주유소 데이터가 사라진 즐겨찾기는 목록에서 제외한다.
        return favorites.mapNotNull { favorite ->
            stations[favorite.gasStationId]?.let { station ->
                FavoriteResponse.from(favorite, station, fuelType, prices[favorite.gasStationId])
            }
        }
    }
}

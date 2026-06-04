package com.kumoh.lbs.auth

import com.kumoh.lbs.gasstation.domain.FuelType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class UserProfileRequest(
    @field:NotBlank(message = "이름은 비어 있을 수 없습니다")
    val name: String,

    val carModel: String? = null,

    val fuelType: FuelType? = null,

    @field:Positive(message = "연비는 양수여야 합니다")
    val fuelEfficiency: Double? = null
)

data class UserProfileResponse(
    val id: Long,
    val name: String?,
    val carModel: String?,
    val fuelType: FuelType?,
    val fuelEfficiency: Double?
) {
    companion object {
        fun from(user: User): UserProfileResponse =
            UserProfileResponse(
                id = user.id,
                name = user.name,
                carModel = user.carModel,
                fuelType = user.fuelType,
                fuelEfficiency = user.fuelEfficiency
            )
    }
}

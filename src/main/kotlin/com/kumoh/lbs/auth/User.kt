package com.kumoh.lbs.auth

import com.kumoh.lbs.gasstation.domain.FuelType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 카카오 로그인 사용자. 카카오에서는 식별자(sub)만 받고,
 * 이름과 차량 프로필(차종·연료종류·연비)은 클라이언트가 직접 입력한다.
 */
@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "kakao_sub", nullable = false, unique = true)
    val kakaoSub: String,

    @Column
    var name: String? = null,

    @Column(name = "car_model")
    var carModel: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type")
    var fuelType: FuelType? = null,

    @Column(name = "fuel_efficiency")
    var fuelEfficiency: Double? = null
)

package com.kumoh.lbs.auth

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {

    fun findByKakaoSub(kakaoSub: String): User?
}

package com.kumoh.lbs.gasstation.repository

import com.kumoh.lbs.gasstation.domain.GasStation
import org.springframework.data.jpa.repository.JpaRepository

interface GasStationRepository : JpaRepository<GasStation, String>

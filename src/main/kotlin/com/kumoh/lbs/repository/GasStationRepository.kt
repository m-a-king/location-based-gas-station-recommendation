package com.kumoh.lbs.repository

import com.kumoh.lbs.domain.GasStation
import org.springframework.data.jpa.repository.JpaRepository

interface GasStationRepository : JpaRepository<GasStation, String>

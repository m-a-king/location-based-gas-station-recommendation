package com.kumoh.lbs.gasstation.batch.repository

import com.kumoh.lbs.gasstation.batch.domain.OpinetCsvHistory
import org.springframework.data.jpa.repository.JpaRepository

interface OpinetCsvHistoryRepository : JpaRepository<OpinetCsvHistory, Long> {
    fun findByFileName(fileName: String): OpinetCsvHistory?
}

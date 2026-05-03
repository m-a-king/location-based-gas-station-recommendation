package com.kumoh.lbs.gasstation.batch.repository

import com.kumoh.lbs.gasstation.batch.domain.OpinetCsvHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface OpinetCsvHistoryRepository : JpaRepository<OpinetCsvHistory, Long> {
    fun findByFileName(fileName: String): OpinetCsvHistory?

    @Query("SELECT MAX(o.updatedAt) FROM OpinetCsvHistory o")
    fun findLatestUpdatedAt(): Instant?
}

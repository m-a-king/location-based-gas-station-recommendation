package com.kumoh.lbs.gasstation.batch.repository

import com.kumoh.lbs.gasstation.batch.domain.BatchMetadata
import org.springframework.data.jpa.repository.JpaRepository

interface BatchMetadataRepository : JpaRepository<BatchMetadata, String>

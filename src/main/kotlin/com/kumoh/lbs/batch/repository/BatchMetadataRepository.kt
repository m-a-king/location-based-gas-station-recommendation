package com.kumoh.lbs.batch.repository

import com.kumoh.lbs.batch.domain.BatchMetadata
import org.springframework.data.jpa.repository.JpaRepository

interface BatchMetadataRepository : JpaRepository<BatchMetadata, String>

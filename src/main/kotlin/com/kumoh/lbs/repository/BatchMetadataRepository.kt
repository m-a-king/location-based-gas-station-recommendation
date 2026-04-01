package com.kumoh.lbs.repository

import com.kumoh.lbs.domain.BatchMetadata
import org.springframework.data.jpa.repository.JpaRepository

interface BatchMetadataRepository : JpaRepository<BatchMetadata, String>

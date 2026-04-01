package com.kumoh.lbs.gasstation.batch.domain

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "batch_metadata")
class BatchMetadata(
    @EmbeddedId
    val id: BatchMetadataId,

    @Column(name = "last_hash", nullable = false)
    var lastHash: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime
)

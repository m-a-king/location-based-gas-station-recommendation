package com.kumoh.lbs.gasstation.batch.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "batch_metadata")
class BatchMetadata(
    @Id
    @Column(name = "file_name")
    val fileName: String,

    @Column(name = "last_hash", nullable = false)
    var lastHash: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime
)

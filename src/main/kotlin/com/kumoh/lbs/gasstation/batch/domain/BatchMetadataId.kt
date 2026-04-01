package com.kumoh.lbs.gasstation.batch.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class BatchMetadataId(
    @Column(nullable = false)
    val source: String,

    @Column(name = "file_name", nullable = false)
    val fileName: String
) : Serializable

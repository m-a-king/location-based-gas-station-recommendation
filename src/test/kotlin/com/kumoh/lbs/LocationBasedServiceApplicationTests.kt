package com.kumoh.lbs

import com.kumoh.lbs.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class LocationBasedServiceApplicationTests {

    @Test
    fun contextLoads() {
    }

}

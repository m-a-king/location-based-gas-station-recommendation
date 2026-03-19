package com.kumoh.lbs.controller

import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String
)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(400, "Bad Request", e.message ?: "잘못된 요청입니다."))

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(400, "Bad Request", e.message ?: "유효성 검증 실패"))

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(e: HandlerMethodValidationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(400, "Bad Request", "파라미터 유효성 검증 실패"))
}

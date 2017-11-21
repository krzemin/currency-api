package com.example.models


sealed trait ApiResponse {
  def success: Boolean
}

case class SuccessResponse[T] private(success: Boolean, response: T) extends ApiResponse

object SuccessResponse {
  def apply[T](response: T): SuccessResponse[T] = SuccessResponse(success = true, response)
}

case class FailedResponse private(success: Boolean, message: String) extends ApiResponse

object FailedResponse {
  def apply(message: String): FailedResponse = FailedResponse(success = false, message)
}

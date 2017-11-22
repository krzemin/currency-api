package com.example.models

import io.swagger.annotations.{ApiModel, ApiModelProperty}

import scala.annotation.meta.field


sealed trait RestApiResponse {
  def success: Boolean
}

@ApiModel(description = "Successful response")
abstract class SuccessResponse[T] (
  @(ApiModelProperty @field)(value = "entity container", required = true)
  response: T,
  @(ApiModelProperty @field)(value = "response status", required = true, allowableValues = "true")
  success: Boolean = true) extends RestApiResponse

@ApiModel(description = "Unsuccessful response")
case class FailedResponse private(
  @(ApiModelProperty @field)(value = "response status", required = true, allowableValues = "false")
  success: Boolean,
  @(ApiModelProperty @field)(value = "error message", required = true)
  message: String) extends RestApiResponse

object FailedResponse {
  def apply(message: String): FailedResponse = FailedResponse(success = false, message)
}

case class StringResponse(response: String, success: Boolean = true)
  extends SuccessResponse(response, success)

case class ObjectResponse(response: Map[String, String], success: Boolean = true)
  extends SuccessResponse(response, success)

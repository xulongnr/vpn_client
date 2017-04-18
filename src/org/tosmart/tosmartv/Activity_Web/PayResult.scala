package org.tosmart.tosmartv.Activity_Web

/**
  * Created by XuLong on 2016/3/24, auto imported from AliPay's demo class
  */

class PayResult(val rawResult: String) {
  private var resultStatus: String = null
  private var result: String = null
  private var memo: String = null
  private var sign: String = null
  private var sign_type: String = null
  private var success: Boolean = false
  val resultParams: Array[String] = rawResult.split(";")

  for (resultParam <- resultParams) {
    if (resultParam.startsWith("resultStatus")) {
      resultStatus = gatValue(resultParam, "resultStatus")
    }
    if (resultParam.startsWith("result")) {
      result = gatValue(resultParam, "result")
    }
    if (resultParam.startsWith("memo")) {
      memo = gatValue(resultParam, "memo")
    }
  }

  val urlParams: Array[String] = result.split("&")
  for (urlParam <- urlParams) {
    if (urlParam.startsWith("sign")) {
      sign = getParamValue(urlParam, "sign")
    }
    if (urlParam.startsWith("sign_type")) {
      sign_type = getParamValue(urlParam, "sign_type")
    }
    if (urlParam.startsWith("success")) {
      success = getParamValue(urlParam, "success").toBoolean
    }
  }

  override def toString: String = "resultStatus={" + resultStatus + "};memo={" + memo + "};result={" + result + "}"

  private def gatValue(content: String, key: String): String = {
    val prefix: String = key + "={"
    content.substring(content.indexOf(prefix) + prefix.length, content.lastIndexOf("}"))
  }
  def getParamValue(content: String, key: String): String = {
    val prefix: String = key + "=\""
    val index: Int = content.indexOf(prefix) + prefix.length
    content.substring(index, content.indexOf("\"", index))
  }

  def getSign: String = sign
  def getSignType: String = sign_type
  def getSuccess: Boolean = success
  def getResultStatus: String = resultStatus
  def getMemo: String = memo
  def getResult: String = result

  def getResultForSign: String = {
    if (!result.isEmpty) {
      val suffix = "&sign_type="
      result.substring(0, result.indexOf(suffix))
    } else result
  }
}
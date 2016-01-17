package ib.execution

import java.time.{LocalDateTime}
import java.time.format.DateTimeFormatter

import ib.IBContract

object `package`{
  val format = DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss")
}

case class IBExecution(orderId: Int,
                      orderRef: String,
                      clientId: Int,
                      execId: String,
                      time: LocalDateTime,
                      exchange: String,
                      side: String,
                      price: Double,
                      cumQty: Int,
                      shares: Int,
                      avgPrice: Double)

case class IBExecutionEvent(reqId: Int, contract: IBContract, execution: IBExecution)

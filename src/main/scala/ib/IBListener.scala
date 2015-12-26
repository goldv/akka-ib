package ib

import java.time.LocalDateTime

import akka.actor.ActorRef
import com.ib.client._
import ib.IBSessionActor.ConnectionClosed
import ib.execution.{IBExecutionEvent, IBExecution}
import ib.marketdata.IBMarketDataActor.{IBTickPrice, IBTickSize}
import ib.order.IBOrderActor.IBOrderStatus
import ib.order.IBOrderIdGenerator.NextValidOrderId

class IBListener(orderHandler: ActorRef,
                 orderIDGenerator: ActorRef,
                 marketDataHandler: ActorRef,
                 referenceDataHandler: ActorRef,
                 executionHandler: ActorRef,
                 sessionHandler: ActorRef) extends EWrapper{

  /*
   ***************************** MARKET DATA ****************************************************
   */
  override def tickPrice(tickerId: Int, field: Int, price: Double, canAutoExecute: Int): Unit = marketDataHandler ! IBTickPrice(tickerId, field, price, canAutoExecute)

  override def tickString(tickerId: Int, tickType: Int, value: String): Unit = {}

  override def tickSize(tickerId: Int, field: Int, size: Int): Unit = marketDataHandler ! IBTickSize(tickerId, field, size )

  override def tickOptionComputation(tickerId: Int, field: Int, impliedVol: Double, delta: Double, optPrice: Double, pvDividend: Double, gamma: Double, vega: Double, theta: Double, undPrice: Double): Unit = {}

  override def tickSnapshotEnd(reqId: Int): Unit = {}

  override def realtimeBar(reqId: Int, time: Long, open: Double, high: Double, low: Double, close: Double, volume: Long, wap: Double, count: Int): Unit = {}

  override def tickGeneric(tickerId: Int, tickType: Int, value: Double): Unit = {}

  override def tickEFP(tickerId: Int, tickType: Int, basisPoints: Double, formattedBasisPoints: String, impliedFuture: Double, holdDays: Int, futureExpiry: String, dividendImpact: Double, dividendsToExpiry: Double): Unit = {}


  /*
   ********************************** ORDER HANDLING *********************************************
   */

  override def orderStatus(orderId: Int, status: String, filled: Int, remaining: Int, avgFillPrice: Double, permId: Int, parentId: Int, lastFillPrice: Double, clientId: Int, whyHeld: String): Unit = {
    println(s"status: orderId $orderId st: $status")
    orderHandler ! IBOrderStatus(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld)
  }

  override def nextValidId(orderId: Int): Unit = { orderIDGenerator ! NextValidOrderId(orderId) }

  override def openOrder(orderId: Int, contract: Contract, order: Order, orderState: OrderState): Unit = {}

  override def openOrderEnd(): Unit = {}


  /*
  *********************************** EXECUTIONS ************************************************
   */
  override def execDetails(reqId: Int, contract: Contract, execution: Execution): Unit = {
    val exec = IBExecution(execution.m_orderId,
      execution.m_clientId,
      execution.m_execId,
      LocalDateTime.parse(execution.m_time, ib.execution.format),
      execution.m_exchange,
      execution.m_side,
      execution.m_price,
      execution.m_cumQty,
      execution.m_shares,
      execution.m_avgPrice)

    executionHandler ! IBExecutionEvent(reqId, IBContract().fromContract(contract), exec)
  }

  override def execDetailsEnd(reqId: Int): Unit = {
    println(s"exec details end $reqId")
  }

  /*
  ************************************ PORTFOLIO **************************************************
   */

  override def position(account: String, contract: Contract, pos: Int, avgCost: Double): Unit = {
    println(s"POSITION: account $account pos: $pos avgCost: $avgCost contract: ${contract.m_symbol}")
  }

  override def accountSummaryEnd(reqId: Int): Unit = {}

  override def updateAccountTime(timeStamp: String): Unit = {}

  override def updatePortfolio(contract: Contract, position: Int, marketPrice: Double, marketValue: Double, averageCost: Double, unrealizedPNL: Double, realizedPNL: Double, accountName: String): Unit = {
    println(s"contract: ${contract.m_symbol} position: $position marketPrice: $marketPrice marketValue: $marketValue averageCost: $averageCost unrealizedPNL: $unrealizedPNL realizedPNL: $realizedPNL")

  }

  override def updateAccountValue(key: String, value: String, currency: String, accountName: String): Unit = {}

  override def positionEnd(): Unit = {}

  override def accountSummary(reqId: Int, account: String, tag: String, value: String, currency: String): Unit = {}




  override def bondContractDetails(reqId: Int, contractDetails: ContractDetails): Unit = {}

  override def managedAccounts(accountsList: String): Unit = {}

  override def displayGroupList(reqId: Int, groups: String): Unit = {}

  override def receiveFA(faDataType: Int, xml: String): Unit = {}

  override def marketDataType(reqId: Int, marketDataType: Int): Unit = {}

  override def updateMktDepthL2(tickerId: Int, position: Int, marketMaker: String, operation: Int, side: Int, price: Double, size: Int): Unit = {}



  override def verifyCompleted(isSuccessful: Boolean, errorText: String): Unit = {}

  override def scannerDataEnd(reqId: Int): Unit = {}

  override def deltaNeutralValidation(reqId: Int, underComp: UnderComp): Unit = {}



  override def currentTime(time: Long): Unit = {}

  override def updateNewsBulletin(msgId: Int, msgType: Int, message: String, origExchange: String): Unit = {}


  override def contractDetailsEnd(reqId: Int): Unit = {}

  override def contractDetails(reqId: Int, contractDetails: ContractDetails): Unit = {}

  override def scannerParameters(xml: String): Unit = {}

  override def updateMktDepth(tickerId: Int, position: Int, operation: Int, side: Int, price: Double, size: Int): Unit = {}

  override def historicalData(reqId: Int, date: String, open: Double, high: Double, low: Double, close: Double, volume: Int, count: Int, WAP: Double, hasGaps: Boolean): Unit = {}

  override def verifyMessageAPI(apiData: String): Unit = {}


  override def fundamentalData(reqId: Int, data: String): Unit = {}


  override def accountDownloadEnd(accountName: String): Unit = {}


  override def commissionReport(commissionReport: CommissionReport): Unit = {}

  override def scannerData(reqId: Int, rank: Int, contractDetails: ContractDetails, distance: String, benchmark: String, projection: String, legsStr: String): Unit = {}

  override def displayGroupUpdated(reqId: Int, contractInfo: String): Unit = {}

  override def error(e: Exception): Unit = {println(s"error ex: $e")}

  override def error(str: String): Unit = {println(s"error msg: $str")}

  override def error(id: Int, errorCode: Int, errorMsg: String): Unit = {println(s"error $errorMsg")}

  override def connectionClosed(): Unit = { sessionHandler ! ConnectionClosed }
}


object IBListener{
  // messages to the subscriber
  trait IBEvent{ def contract: IBContract }
  trait IBTickerIdEvent{ def tickerId: Int }
}
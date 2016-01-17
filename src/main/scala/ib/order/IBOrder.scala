package ib.order

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.ib.client.Order
import ib.order.IBOrder.{IOrderAction, IBOrderType, IBOrderAction}

object `package`{
  val format = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss")
}

case class IBOrder(totalQuantity: Option[Int] = None,
                   action: Option[IBOrderAction] = None,
                   orderType: Option[IBOrderType] = None,
                   transmit: Option[Boolean] = None,
                   account: Option[String] = None,
                   limitPrice: Option[Double] = None,
                   auxPrice: Option[Double] = None,
                   goodTilDate: Option[ZonedDateTime] = None) {

  def withTotalQuantity(quantity: Int) = this.copy(totalQuantity = Some(quantity))
  def withAction(action: IBOrderAction) = this.copy(action = Some(action))
  def withOrderType(orderType: IBOrderType) = this.copy(orderType = Some(orderType))
  def withTransmit(transmit: Boolean) = this.copy(transmit = Some(transmit))
  def withAccount(account: String) = this.copy(account = Some(account))
  def withLimitPrice(limitPrice: Double) = this.copy(limitPrice = Some(limitPrice))
  def withAuxPrice(auxPrice: Double) = this.copy(auxPrice = Some(auxPrice))
  def withGoodTilDate(goodTilDate: ZonedDateTime) = this.copy(goodTilDate = Some(goodTilDate))

  def toOrder(orderRef: String) = {
    val order = new Order
    order.m_orderRef = orderRef
    totalQuantity.foreach(order.m_totalQuantity = _)
    action.foreach(a => order.m_action = a.value)
    orderType.foreach(ot => order.m_orderType = ot.value)
    transmit.foreach(order.m_transmit = _)
    account.foreach(order.m_account = _)
    limitPrice.foreach(order.m_lmtPrice = _)
    auxPrice.foreach(order.m_auxPrice = _)
    goodTilDate.foreach(gtd => order.m_goodTillDate = gtd.format(format))
    order
  }

  def fromOrder(order: Order) = IBOrder(
    totalQuantity = Option(order.m_totalQuantity),
    action = Option(IOrderAction.fromString(order.m_action)),
    orderType = Option(IBOrderType.fromString(order.m_orderType)),
    transmit = Option(order.m_transmit),
    account = Option(order.m_account),
    limitPrice = Option(order.m_lmtPrice),
    auxPrice = Option(order.m_auxPrice),
    goodTilDate = Option(order.m_goodTillDate).map(d => ZonedDateTime.parse(d, format))
  )

}


object IBOrder{

  sealed trait IBOrderAction{ def value: String }
  case object Buy extends IBOrderAction{ lazy val value = "BUY" }
  case object Sell extends IBOrderAction{ lazy val value = "SELL" }

  object IOrderAction{
    def fromString(str: String) = str match{
      case "BUY" => Buy
      case "SELL" => Sell
    }
  }

  sealed trait IBOrderType{ def value: String }
  case object Market extends IBOrderType{ lazy val value = "MKT" }
  case object MarketOnClose extends IBOrderType{ lazy val value = "MOC" }
  case object Limit extends IBOrderType{ lazy val value = "LMT" }
  case object Stop extends IBOrderType{ lazy val value = "STP" }

  object IBOrderType{
    def fromString(str: String) = str match{
      case "MKT" => Market
      case "MOC" => MarketOnClose
      case "LMT" => Limit
      case "STP" => Stop
    }
  }

  sealed trait IBTimeInForce{ def value: String }
  case object Day extends IBTimeInForce{ lazy val value = "DAY"}
  case object FillOrKill extends IBTimeInForce{ lazy val value = "FOK"}
  case object GoodTilCancelled extends IBTimeInForce{ lazy val value = "GTC"}
  case object GoodTilDate extends IBTimeInForce{ lazy val value = "GTD"}

  object IBTimeInForce{
    def fromString(str: String) = str match{
      case "DAY" => Day
      case "FOK" => FillOrKill
      case "GTC" => GoodTilCancelled
      case "GTD" => GoodTilDate
    }
  }

}
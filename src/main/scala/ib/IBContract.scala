package ib

import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.ib.client.Contract

object `package`{
  val expiryFormatter = DateTimeFormatter.ofPattern("yyyyMM")
  val decimalFormat = new DecimalFormat("#.#######")
}

case class IBContract(conId: Option[Int] = None,
                      symbol: Option[String] = None,
                      secType:  Option[String] = None,
                      expiry: Option[String] = None,
                      strike: Option[Double] = None,
                      right: Option[String] = None,
                      multiplier: Option[String] = None,
                      exchange: Option[String] = None,
                      localSymbol: Option[String] = None,
                      tradingClass: Option[String] = None,
                      primaryExch: Option[String] = None,
                      includeExpired: Option[Boolean] = None,
                      secIdType: Option[String] = None,
                      secId: Option[String] = None,
                      comboLegsDescrip: Option[String] = None,
                      currency: Option[String] = None){

  def withConId(conId: Int) = this.copy(conId = Some(conId))
  def withSymbol(symbol: String) = this.copy(symbol = Some(symbol))
  def withSecType(secType: String) = this.copy(secType = Some(secType))
  def withExpiry(expiry: LocalDate) = this.copy(expiry = Some(expiry.format(expiryFormatter)))
  def withStrike(strike: Double) = this.copy(strike = Some(strike))
  def withRight(right: String) = this.copy(right = Some(right))
  def withMultiplier(multiplier: Double)= this.copy(multiplier = Some(decimalFormat.format(multiplier)))
  def withExchange(exchange: String) = this.copy(exchange = Some(exchange))
  def witLocalSymbol(localSymbol: String) = this.copy(localSymbol = Some(localSymbol))
  def withTradingClass(tradingClass: String) = this.copy( tradingClass = Some(tradingClass))
  def withPrimaryExch(primaryExch: String) = this.copy( primaryExch = Some(primaryExch))
  def withIncludeExpired(includeExpired: Boolean) = this.copy( includeExpired = Some(includeExpired))
  def withSecIdType(secIdType: String) = this.copy( secIdType = Some(secIdType))
  def withSecId(secId: String) = this.copy( secId = Some(secId) )
  def withComboLegsDescrip(comboLegsDescrip: String) = this.copy( comboLegsDescrip = Some(comboLegsDescrip) )
  def withCurrency(currency: String) = this.copy( currency = Some(currency) )

  def toContract = {
    val contract = new Contract()
    conId.foreach(contract.m_conId = _)
    symbol.foreach(contract.m_symbol = _)
    secType.foreach(contract.m_secType = _)
    expiry.foreach(contract.m_exchange = _)
    strike.foreach(contract.m_strike = _)
    right.foreach(contract.m_right = _)
    multiplier.foreach(contract.m_multiplier = _)
    exchange.foreach(contract.m_exchange = _)
    localSymbol.foreach(contract.m_localSymbol = _)
    tradingClass.foreach(contract.m_tradingClass = _)
    primaryExch.foreach(contract.m_primaryExch = _)
    includeExpired.foreach(contract.m_includeExpired = _)
    secIdType.foreach(contract.m_secIdType = _)
    secId.foreach(contract.m_secId = _)
    comboLegsDescrip.foreach(contract.m_comboLegsDescrip = _)
    currency.foreach( contract.m_currency = _ )
    contract
  }

  def fromContract(contract: Contract) = IBContract(
    conId = Option(contract.m_conId),
    symbol = Option(contract.m_symbol),
    secType = Option(contract.m_secType),
    expiry = Option(contract.m_expiry),
    strike = Option(contract.m_strike),
    right = Option(contract.m_right),
    multiplier = Option(contract.m_multiplier),
    exchange = Option(contract.m_exchange),
    localSymbol = Option(contract.m_localSymbol),
    tradingClass = Option(contract.m_tradingClass),
    primaryExch = Option(contract.m_primaryExch),
    includeExpired = Option(contract.m_includeExpired),
    secIdType = Option(contract.m_secIdType),
    secId = Option(contract.m_secId),
    comboLegsDescrip = Option(contract.m_comboLegsDescrip),
    currency = Option(contract.m_currency)
  )
}



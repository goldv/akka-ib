package ib

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class IBContractSpec  extends WordSpecLike with Matchers with BeforeAndAfterAll{

  "an ib contract" must{
    "create a contract with a con id" in{
      val con = IBContract().withConId(5)
      con.toContract.m_conId should be (5)
    }
  }

}

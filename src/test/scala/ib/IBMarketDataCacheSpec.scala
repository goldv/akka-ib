package ib

import com.ib.client.TickType
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ib.marketdata.{CacheUpdates, IBMarketDataActor}
import IBMarketDataActor.{IBTickSize, IBTickPrice, IBMarketDataPrice}

class IBMarketDataCacheSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with CacheUpdates{

  val contractCache = Map(1 -> IBContract().withConId(1))

  "price cache" must{
    "handle incremental price updates" in{

      val cache = for{
        _ <- updatePriceCachePrice(IBTickPrice(1, TickType.BID, 1.0, 0))
        _ <- updatePriceCachePrice(IBTickPrice(1, TickType.ASK, 2.0, 0))
        _ <- updatePriceCacheSize(IBTickSize(1, TickType.BID_SIZE, 100))
        _ <- updatePriceCacheSize(IBTickSize(1, TickType.ASK_SIZE, 200))
      } yield ()

      val (pcache, _) = cache.exec((Map.empty[Int,IBMarketDataPrice], contractCache))

      pcache(1).contract should be(IBContract().withConId(1))
      pcache(1).bid should be(1)
      pcache(1).ask should be(2)
      pcache(1).askSize should be(200)
      pcache(1).bidSize should be(100)
    }
  }

}

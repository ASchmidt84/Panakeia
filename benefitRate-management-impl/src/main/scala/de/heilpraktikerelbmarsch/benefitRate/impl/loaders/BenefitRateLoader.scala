package de.heilpraktikerelbmarsch.benefitRate.impl.loaders

import akka.cluster.sharding.typed.scaladsl.Entity
import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServerComponents}
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.benefitRate.api.services.BenefitRateService
import de.heilpraktikerelbmarsch.benefitRate.impl.services.BenefitRateServiceImpl
import de.heilpraktikerelbmarsch.security.api.components.SecurityComponents
import org.pac4j.http.client.direct.HeaderClient
import play.api.Environment
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.softwaremill.macwire.wire
import de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates.{BenefitRate, BenefitRateRepository, SerializerRegistry}

import scala.concurrent.ExecutionContext

class BenefitRateLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new BenefitRateApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new BenefitRateApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[BenefitRateService])

}


trait BenefitRateServiceComponents extends LagomServerComponents
  with SecurityComponents
  with SlickPersistenceComponents
  with HikariCPComponents
  with LagomKafkaComponents
  with AhcWSComponents {

  import net.ceedubs.ficus.Ficus._
  import scala.concurrent.duration._

  implicit def executionContext: ExecutionContext
  def environment: Environment

  implicit def materializer: Materializer
  implicit def c: Config = this.configuration.underlying

  override val jwtClient: HeaderClient = headerClient

  //Lagom Server
  override lazy val lagomServer = serverFor[BenefitRateService](wire[BenefitRateServiceImpl])

  //Json Registry
  lazy val jsonSerializerRegistry = SerializerRegistry
  private implicit val mode = environment.mode

  //Repos
  lazy val globalRepository = wire[BenefitRateRepository]
  //Event Processors
//  readSide.register(wire[ProfileProcessor])



  // Initialize the sharding for the ShoppingCart aggregate.
  // See https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html
  clusterSharding.init(
    Entity(BenefitRate.typedKey) { entityContext =>
      BenefitRate(entityContext)
    }
  )

}


abstract class BenefitRateApplication(context: LagomApplicationContext)
  extends LagomApplication(context) with BenefitRateServiceComponents
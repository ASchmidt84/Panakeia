package de.heilpraktikerelbmarsch.benefitRate.impl.loaders

import java.util.UUID

import akka.cluster.sharding.typed.scaladsl.Entity
import akka.stream.Materializer
import akka.util.Timeout
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
import de.heilpraktikerelbmarsch.benefitRate.api.adt.Rate
import de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates.BenefitRate.{CommandAccepted, CommandRejected, Confirmation}
import de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates.{BenefitRate, BenefitRateProcessor, BenefitRateRepository, SerializerRegistry}
import de.heilpraktikerelbmarsch.util.adt.benefits.SettlementType
import de.heilpraktikerelbmarsch.util.adt.contacts.{Operator, SystemOperator}
import de.heilpraktikerelbmarsch.util.adt.security.MicroServiceIdentifier.BenefitRateServiceIdentifier
import squants.market.EUR

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

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

  //Event Processors
  lazy val processor: BenefitRateProcessor = wire[BenefitRateProcessor]
  readSide.register(processor)

  //Repos
  lazy val globalRepository = wire[BenefitRateRepository]




  // Initialize the sharding for the ShoppingCart aggregate.
  // See https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html
  clusterSharding.init(
    Entity(BenefitRate.typedKey) { entityContext =>
      BenefitRate(entityContext)
    }
  )

}


abstract class BenefitRateApplication(context: LagomApplicationContext)
  extends LagomApplication(context) with BenefitRateServiceComponents {

  //TODO Init von standard BenefitRate´s damit ein Grundstock vorhanden ist

  import net.ceedubs.ficus.Ficus._

  importBaseData(config.as[String]("init.csv.gebüh"))


  private def importBaseData(fileName: String): Unit = {
    println("Starting importing GebüH Data")
    import scala.concurrent.duration._
    import com.github.tototoshi.csv._
    implicit val timeout = Timeout(8 seconds)

    object WindowsCSVFormat extends DefaultCSVFormat {
      override val delimiter = ';'
    }
    val numberKey = "Ziffer"
    val descriptionKey = "Leistungsbeschreibung"
    val pkvKey = "PKV"
    val beihilfeKey = "Beihilfe"
    val postBKey = "Post B"
    val gebUEHKey = "GebüH"
    val szKEy = "SZ"


    val reader = CSVReader.open(fileName)( WindowsCSVFormat )
    val futSeq = reader.iteratorWithHeaders.map{row =>
      val number = row(numberKey)
      println(s"Row data with number $number")

      globalRepository.findByNumber(number).flatMap{
        case Some(_) => Future.successful( s"$number already exists" )
        case _ =>
          val id = UUID.randomUUID()
          val createFut = clusterSharding.entityRefFor(BenefitRate.typedKey,id.toString).ask[Confirmation](reply => BenefitRate.CreateBenefitRate(
            number,
            row(descriptionKey),
            true,
            None,
            reply,
            SystemOperator(BenefitRateServiceIdentifier.name)
          ))
          createFut.flatMap{
            case CommandAccepted(_) =>
              val rates = Seq(
                Option( row(pkvKey) ).filter(_.trim.nonEmpty).map(i => Rate(EUR(i.replace(",",".").toDouble),SettlementType.PKV1) ),
                Option( row(postBKey) ).filter(_.trim.nonEmpty).map(i => Rate(EUR(i.replace(",",".").toDouble), SettlementType.PBKK ) ),
                Option( row(gebUEHKey) ).filter(_.trim.nonEmpty).map(i => Rate(EUR(i.replace(",",".").toDouble), SettlementType.SZGebueH ) ),
                Option( row(szKEy) ).filter(_.trim.nonEmpty).map(i => Rate(EUR(i.replace(",",".").toDouble*1.1), SettlementType.SZPauschal ) )
              ) ++
                SettlementType.allBeihilfe.map(settlementType =>
                  Option( row(beihilfeKey) ).filter(_.trim.nonEmpty).map(i => Rate(EUR(i.replace(",",".").toDouble), settlementType ) )
                )
              Future.sequence(rates.filter(_.isDefined).map{u =>
                clusterSharding.entityRefFor(BenefitRate.typedKey,id.toString).ask[Confirmation](r => BenefitRate.SetRate(u.get,r,SystemOperator(BenefitRateServiceIdentifier.name)))
              }).map{u =>
                u.map(i => s"$number imported: ${i.toString}\n").mkString("------------------\n\n")
              }
            case CommandRejected(error) =>
              Future.successful(error)
          }
      }
    }

    Future.sequence(futSeq).map{println}

  }


}
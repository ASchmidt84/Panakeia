package de.heilpraktikerelbmarsch.file.impl.loaders

import akka.cluster.sharding.typed.scaladsl.Entity
import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServerComponents}
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.file.api.services.FileService
import de.heilpraktikerelbmarsch.security.api.components.SecurityComponents
import org.pac4j.http.client.direct.HeaderClient
import play.api.Environment
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.softwaremill.macwire.wire
import de.heilpraktikerelbmarsch.file.impl.file.{PatientFile, PatientFileProcessor, PatientFileRepository, SerializerRegistry}
import de.heilpraktikerelbmarsch.file.impl.services.FileServiceImpl
import de.heilpraktikerelbmarsch.file.impl.subscriber.PatientSubscriber
import de.heilpraktikerelbmarsch.patient.api.services.PatientService

import scala.concurrent.ExecutionContext

class PatientFileServiceLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new PatientFileServiceApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new PatientFileServiceApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[FileService])

}



trait PatientFileServiceComponents extends LagomServerComponents
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

  val patientService: PatientService

  //Lagom Server
  override lazy val lagomServer = serverFor[FileService](wire[FileServiceImpl])

  //Json Registry
  lazy val jsonSerializerRegistry = SerializerRegistry
  private implicit val mode = environment.mode

  //Event Processors
  lazy val processor: PatientFileProcessor = wire[PatientFileProcessor]
  readSide.register(processor)

  //Repos
  lazy val globalRepository = wire[PatientFileRepository]



  // Initialize the sharding for the ShoppingCart aggregate.
  // See https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html
  clusterSharding.init(
    Entity(PatientFile.typedKey) { entityContext =>
      PatientFile(entityContext)
    }
  )

}

abstract class PatientFileServiceApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with PatientFileServiceComponents {
  override lazy val patientService: PatientService = serviceClient.implement[PatientService]

  val subscriber: PatientSubscriber = new PatientSubscriber(patientService,clusterSharding,globalRepository)(executionContext)

}
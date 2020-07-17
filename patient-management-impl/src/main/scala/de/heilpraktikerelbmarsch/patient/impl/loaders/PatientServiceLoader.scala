package de.heilpraktikerelbmarsch.patient.impl.loaders

import akka.cluster.sharding.typed.scaladsl.Entity
import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServerComponents}
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.patient.api.services.PatientService
import de.heilpraktikerelbmarsch.security.api.components.SecurityComponents
import org.pac4j.http.client.direct.HeaderClient
import play.api.Environment
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.softwaremill.macwire.wire
import de.heilpraktikerelbmarsch.patient.impl.patient.{Patient, PatientProcessor, PatientRepository, SerializerRegistry}
import de.heilpraktikerelbmarsch.patient.impl.services.PatientServiceImpl

import scala.concurrent.ExecutionContext

class PatientServiceLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new PatientServiceApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new PatientServiceApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[PatientService])

}


trait PatientServiceComponents extends LagomServerComponents
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

  override lazy val jwtClient: HeaderClient = headerClient



  //Json Registry
  lazy val jsonSerializerRegistry = SerializerRegistry
  private implicit val mode = environment.mode

  //Event Processors
  lazy val processor: PatientProcessor = wire[PatientProcessor]
  readSide.register(processor)

  //Repos
  lazy val globalRepository = wire[PatientRepository]


  // Initialize the sharding for the ShoppingCart aggregate.
  // See https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html
  clusterSharding.init(
    Entity(Patient.typedKey) { entityContext =>
      Patient(entityContext)
    }
  )

}

abstract class PatientServiceApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with PatientServiceComponents {
  //Lagom Server
  override lazy val lagomServer = serverFor[PatientService](wire[PatientServiceImpl])
}
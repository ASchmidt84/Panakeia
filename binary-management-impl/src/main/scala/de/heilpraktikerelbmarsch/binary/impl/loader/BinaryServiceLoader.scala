package de.heilpraktikerelbmarsch.binary.impl.loader

import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServerComponents}
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.binary.api.services.BinaryService
import de.heilpraktikerelbmarsch.binary.impl.services.BinaryServiceImpl
import de.heilpraktikerelbmarsch.security.api.components.SecurityComponents
import org.pac4j.http.client.direct.HeaderClient
import play.api.Environment
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.softwaremill.macwire.wire
import de.heilpraktikerelbmarsch.binary.impl.repository.BinaryRepository
import de.heilpraktikerelbmarsch.binary.impl.router.FileUploadRouter
import de.heilpraktikerelbmarsch.binary.impl.storage.{MinioStorageImpl, Storage}

import scala.concurrent.ExecutionContext

class BinaryServiceLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new BinaryServiceApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new BinaryServiceApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[BinaryService])

}


trait BinaryServiceComponents extends LagomServerComponents
  with SecurityComponents
  with SlickPersistenceComponents
  with HikariCPComponents
  with AhcWSComponents {
  import net.ceedubs.ficus.Ficus._

  implicit def executionContext: ExecutionContext
  def environment: Environment

  implicit def materializer: Materializer
  implicit def c: Config = this.configuration.underlying

  override val jwtClient: HeaderClient = headerClient

  lazy val storage: Storage = MinioStorageImpl(c.as[String]("minio.endpoint"),c.as[String]("minio.accessKey"),c.as[String]("minio.secretKey"))

  //Json Registry
  lazy val jsonSerializerRegistry = SerializerRegistry
  private implicit val mode = environment.mode

  lazy val binaryRepository = wire[BinaryRepository]

}

abstract class BinaryServiceApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with BinaryServiceComponents {

  //Lagom Server
  override lazy val lagomServer = serverFor[BinaryService](wire[BinaryServiceImpl])
    .additionalRouter(wire[FileUploadRouter].router) //hier kommt der Router zum tragen!

}

object SerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Nil
}
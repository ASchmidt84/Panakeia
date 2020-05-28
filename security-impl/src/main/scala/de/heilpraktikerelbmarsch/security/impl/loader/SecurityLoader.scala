package de.heilpraktikerelbmarsch.security.impl.loader

import java.util.UUID

import akka.Done
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServerComponents}
import com.softwaremill.macwire.wire
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.security.api.components.SecurityComponents
import de.heilpraktikerelbmarsch.security.api.services.SecurityService
import de.heilpraktikerelbmarsch.security.impl.profiles.{Profile, ProfileProcessor, ProfileRepository, ProfileSerializerRegistry}
import de.heilpraktikerelbmarsch.security.impl.services.SecurityServiceImpl
import org.pac4j.http.client.direct.HeaderClient
import play.api.Environment
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import de.heilpraktikerelbmarsch.security.impl.profiles.Profile.{CreateProfile, Get, ProfileCmdAccepted, ProfileCmdRejected, ProfileConfirmation}
import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData}
import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles.SuperAdminRole

import scala.concurrent.{ExecutionContext, Future}

class SecurityLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new SecurityApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new SecurityApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[SecurityService])

}


trait SecurityServiceComponents extends LagomServerComponents
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

  //  val securityService: SecurityService
  //Lagom Server
  override lazy val lagomServer = serverFor[SecurityService](wire[SecurityServiceImpl])
  //Json Registry
  lazy val jsonSerializerRegistry = ProfileSerializerRegistry
  private implicit val mode = environment.mode

  //Repos
  lazy val profileRepository = wire[ProfileRepository]
  //Event Processors
  readSide.register(wire[ProfileProcessor])

  //Entities
//  persistentEntityRegistry.register(wire[FullFillerEntity])


//  readSide.register(wire[FullFillerProcessor])



  // Initialize the sharding for the ShoppingCart aggregate.
  // See https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html
  clusterSharding.init(
    Entity(Profile.typedKey) { entityContext =>
      Profile(entityContext)
    }
  )

  //Prüfen ob er haupt user angelegt worden ist!
  private val username = c.as[String]("user.init.username")
  private val password = c.as[String]("user.init.password")

  this.actorSystem.scheduler.scheduleOnce(5.seconds){
    profileRepository.findUUIDByEmail(username)
      .flatMap{
        case None => //NICHT DA!
          val newUUID = UUID.randomUUID()
          clusterSharding.entityRefFor(Profile.typedKey,newUUID.toString).ask[ProfileConfirmation](reply => CreateProfile(username,password,Some(EmailAddress(username)),Set(SuperAdminRole), PersonalData("","",""),reply ))(30.seconds).map{
            case ProfileCmdAccepted(profile) => s"Profile ${profile.login} created"
            case ProfileCmdRejected(err) => s"ERROR while default user init: $err"
            case a => s"a: $a"
          }
        case Some(id) =>
          clusterSharding.entityRefFor(Profile.typedKey,id.toString).ask(reply => Get(reply) )(5.seconds).map{
            case ProfileCmdAccepted(d) => d.toString
            case ProfileCmdRejected(d) => d
          }
      }
      .map{r => println(r)}
      .recover(a => println(s"ERROR $a"))
  }
}

abstract class SecurityApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
  with SecurityServiceComponents {





}
package de.heilpraktikerelbmarsch.security.impl.services

import java.util.{Date, UUID}

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}

import scala.concurrent.duration._
import akka.util.Timeout
import com.google.common.collect.ImmutableList
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.{BadRequest, NotFound}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import com.nimbusds.jose.EncryptionMethod.A256CBC_HS512
import com.nimbusds.jose.JWEAlgorithm.DIR
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.crypto.DirectEncrypter
import com.nimbusds.jwt.{EncryptedJWT, JWTClaimsSet}
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.security.api.adt.requests.CreateProfileRequest
import de.heilpraktikerelbmarsch.security.api.services.{SecurityService, SystemSecuredService}
import de.heilpraktikerelbmarsch.security.impl.profiles.{Profile, ProfileRepository}
import de.heilpraktikerelbmarsch.security.impl.profiles.Profile._
import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles
import org.joda.time.DateTime
import org.pac4j.core.context.Pac4jConstants
import org.pac4j.core.profile.definition.CommonProfileDefinition
import org.taymyr.lagom.scaladsl.openapi.OpenAPIServiceImpl

import scala.concurrent.ExecutionContext

//https://github.com/lagom/lagom-samples/blob/1.6.x/shopping-cart/shopping-cart-scala/shopping-cart/src/main/scala/com/example/shoppingcart/impl/ShoppingCartLoader.scala

class SecurityServiceImpl(persistentEntityRegistry: PersistentEntityRegistry,
                          override val securityConfig: org.pac4j.core.config.Config,
                          clusterSharding: ClusterSharding,
                          profileRepository: ProfileRepository)(implicit ec: ExecutionContext, val config: Config) extends SecurityService with SystemSecuredService with OpenAPIServiceImpl {


  private def profileEntity(id: UUID): EntityRef[Command] = clusterSharding.entityRefFor(Profile.typedKey,id.toString)

  implicit val timeout = Timeout(15.seconds)

  private def generateJwt(profile: ProfileSummary, entityId: UUID): String = {
    import scala.jdk.CollectionConverters._
    val jwt = new JWTClaimsSet.Builder()
      .issuer("Panakeia")
      .subject(profile.login)
      .claim("roles", ImmutableList.copyOf( profile.roles.map(_.name).asJava ) ) //Erstmal musst du die Rolle haben dann das Recht! Das Recht wird vom µ-Service SELBER verwaltet!!!
      .claim("userId",profile.login)
      .claim(Pac4jConstants.USERNAME,profile.login)
//      .claim(Pac4jConstants.PASSWORD,profile)
      .claim(CommonProfileDefinition.EMAIL,profile.email.map(_.value).orNull)
      .claim(CommonProfileDefinition.DISPLAY_NAME,profile.name.toString)
      .claim("entityId",entityId.toString)
      .claim("displayName", profile.name.toString)
      .expirationTime(new DateTime().plusMinutes(30).toDate)
      .issueTime(new Date)
      .jwtID(profile.login).build()
    val jwe = new EncryptedJWT(new JWEHeader(DIR, A256CBC_HS512), jwt)
    jwe.encrypt(new DirectEncrypter(this.octetSequenceKey))
    jwe.serialize()
  }

  /**
   * Verifies that you are the person who you say you are - (signIn)
   * The servicecall takes the password or key or anything what can confirm that you are you!
   *
   * @param username The user id who you say you are! -> login!!!
   *
   * @return the jwt token!!!!
   */
  override def jwtAuthenticate(username: String): ServiceCall[String, String] = ServiceCall{ password =>
    profileRepository.findUUIDByLogin(username).flatMap{
      case None => throw NotFound(s"The user with $username was not found! Access denied!")
      case Some(id) =>
        profileEntity(id).ask[ProfileConfirmation](reply => ValidatePassword(password,reply)).map{
          case ProfileCmdAccepted(profile) => generateJwt(profile,id)
          case _ => throw BadRequest("Access denied!")
        }
    }
  }

  /**
   * Sends a new JWT with an extended expire date
   *
   * @return JWT String with extended expire date
   */
  override def jwtRegenerate(username: String): ServiceCall[NotUsed, String] = authorize(requireAnyRole(DefaultRoles.values:_*)){ profile => ServerServiceCall{ data =>
    profileRepository.findUUIDByLogin(username).flatMap{
      case Some(id) =>
        profileEntity(id)
          .ask[ProfileConfirmation](reply => Profile.Get(reply))
          .map{
            case ProfileCmdAccepted(pr) => generateJwt(pr,id)
            case _ => throw BadRequest("Profile was not found!")
          }
          .recover{
            case _ => throw BadRequest("Corruption in data")
          }
      case _ => throw NotFound("Requested user was not found")
    }
  }}

  /**
   * Adds a role to the given user
   *
   * @param username
   *
   * @return
   */
  override def addRole(username: String): ServiceCall[String, NotUsed] = authorize(requireAnyRole(adminRoles++systemRoles)){ _ => ServerServiceCall{ data =>
    profileRepository.findUUIDByLogin(username).flatMap{
      case Some(id) =>
        profileEntity(id)
          .ask[ProfileConfirmation](reply => AddRole(DefaultRoles.withName(data),reply) )
          .map{
            case ProfileCmdAccepted(_) => NotUsed
            case _ => throw BadRequest("Your privileges are not enough to execute this command")
          }
          .recover{
            case _ => throw BadRequest("Submitted data are invalid")
          }
      case _ => throw NotFound("Requested user was not found")
    }
  }}

  /**
   * removes a role from a given user
   *
   * @param username
   *
   * @return
   */
  override def removeRole(username: String): ServiceCall[String, NotUsed] = authorize(requireAnyRole( adminRoles++systemRoles )){ _ => ServerServiceCall{ data =>
    profileRepository.findUUIDByLogin(username).flatMap{
      case Some(id) =>
        profileEntity(id)
            .ask[ProfileConfirmation](reply => RemoveRole(DefaultRoles.withName(data),reply))
            .map{
              case ProfileCmdAccepted(_) => NotUsed
              case _ => throw BadRequest("Your privileges are not high enough to execute this command")
            }
            .recover{
              case _ => throw BadRequest("Submitted data are invalid")
            }
      case _ => throw NotFound("Requested user was not found")
    }
  }}

  /**
   * Creates an user
   *
   * @return
   */
  override def createUser(): ServiceCall[CreateProfileRequest, String] = ServiceCall{data =>
    profileRepository.findUUIDByEmailAndLogin(data.userName,data.email.value).flatMap{
      case None =>
        val uuid = UUID.randomUUID()
        profileEntity(uuid)
          .ask[ProfileConfirmation](reply => CreateProfile(data.userName,data.password,Some(data.email), data.roles.toSet, data.personalData, reply ) ).map{
          case ProfileCmdAccepted(pr) => generateJwt(pr,uuid)
          case _ => throw BadRequest("")
        }
      case _ => throw BadRequest("User already exists! Access denied!")
    }
  }

}
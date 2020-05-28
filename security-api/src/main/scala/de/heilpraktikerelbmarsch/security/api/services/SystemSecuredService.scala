package de.heilpraktikerelbmarsch.security.api.services

import java.util.Collections.singletonList
import java.util.Date

import com.google.common.collect.ImmutableList
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.Forbidden
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import com.nimbusds.jose.EncryptionMethod.A256CBC_HS512
import com.nimbusds.jose.JWEAlgorithm.DIR
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.crypto.DirectEncrypter
import com.nimbusds.jose.jwk.{JWK, OctetSequenceKey}
import com.nimbusds.jwt.{EncryptedJWT, JWTClaimsSet}
import de.heilpraktikerelbmarsch.security.api.profiles.{AnonymousJWTProfile, PanakeiaJWTProfile}
import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles._
import de.heilpraktikerelbmarsch.util.adt.security.{DefaultRoles, MicroServiceIdentifier}
import org.joda.time.DateTime
import org.pac4j.core.authorization.authorizer.Authorizer
import org.pac4j.core.client.Client
import org.pac4j.core.config.Config
import org.pac4j.core.credentials.Credentials
import org.pac4j.lagom.scaladsl.LagomWebContext
import play.api.libs.json.Json

/**
 * This trait have to used by µ-services to get access to other µ-services!
 */
trait SystemSecuredService {
  import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer

  import scala.jdk.CollectionConverters._
  import net.ceedubs.ficus.Ficus._

  implicit val config: com.typesafe.config.Config

  protected val adminRoles: List[DefaultRoles] = List(
    SuperAdminRole, AdminRole, SystemRole
  )

  protected val systemRoles: List[DefaultRoles] = List(SystemRole)

  protected val therapistRoles: List[DefaultRoles] = List(TherapistRole)

  final protected lazy val octetSequenceKey: OctetSequenceKey = {
    val hs512 = config.as[Map[String,String]]("pac4j.jwk")
    JWK.parse(Json.toJson(hs512).toString()).asInstanceOf[OctetSequenceKey]
  }

  /**
   * Gives a JWT for Panakeia as System to get access to system for internal use
   * @return
   */
  def getSystemJWT(serviceName: String): String = {
    require(serviceName.trim.nonEmpty, "I need your service name!!!!")
    val jwt = new JWTClaimsSet.Builder()
      .issuer("Panakeia")
      .subject("panakeia-internal")
      .claim("roles", ImmutableList.of(SystemRole.name))
      .claim("Microservice-Name",serviceName)
      //      .claim("", Json.toJson(Map("" -> "")))
      .expirationTime(new DateTime().plusMinutes(30).toDate)
      .issueTime(new Date)
      .jwtID("panakeia-internal").build()
    val jwe = new EncryptedJWT(new JWEHeader(DIR, A256CBC_HS512), jwt)
    jwe.encrypt(new DirectEncrypter(octetSequenceKey))
    jwe.serialize()
  }

  def jwtSystemCall[A,B]: ServiceCall[A, B] => MicroServiceIdentifier => ServiceCall[A,B] = (income: ServiceCall[A,B]) => (service: MicroServiceIdentifier) => income.handleRequestHeader(r => r.addHeader("Authorization",s"Bearer ${getSystemJWT(service.name)}"))


  def requireAnyRole(roleName: DefaultRoles*): RequireAnyRoleAuthorizer[PanakeiaJWTProfile] = {
    requireAnyRole(roleName.toList)
  }

  def requireAnyRole(roleName: List[DefaultRoles]): RequireAnyRoleAuthorizer[PanakeiaJWTProfile] = {
    org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer.requireAnyRole(roleName.map(_.name).asJava)
  }

  def requireAnyRole(roleName: Set[DefaultRoles]): RequireAnyRoleAuthorizer[PanakeiaJWTProfile] = {
    requireAnyRole(roleName.toList)
  }

//  def requireAnyRole(roleName: Set[DefaultRoles]*): RequireAnyRoleAuthorizer[PanakeiaJWTProfile] = {
//    requireAnyRole(roleName.flatten.toList)
//  }

  /**
   * Get configuration of pac4j for this service.
   *
   * @return pac4j configuration
   */
  def securityConfig: Config

  /**
   * Service call composition for authentication.
   *
   * @param serviceCall Service call
   * @tparam Request Type of request
   * @tparam Response Type of response
   * @return Service call with authentication logic
   */
  def authenticate[Request, Response](serviceCall: PanakeiaJWTProfile => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] =
    authenticate(securityConfig.getClients.getDefaultSecurityClients, serviceCall)

  /**
   * Service call composition for authentication.
   *
   * @param clientName Name of authentication client
   * @param serviceCall Service call
   * @tparam Request Type of request
   * @tparam Response Type of response
   * @return Service call with authentication logic
   */
  def authenticate[Request, Response](clientName: String,
                                      serviceCall: PanakeiaJWTProfile => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] =
    ServerServiceCall.compose { requestHeader =>
      val profile = try {
        val clients = securityConfig.getClients
        val defaultClient = clients.findClient(clientName).asInstanceOf[Client[Credentials, PanakeiaJWTProfile]]
        val context = new LagomWebContext(requestHeader)
        val credentials = defaultClient.getCredentials(context)
        val profile: PanakeiaJWTProfile = PanakeiaJWTProfile(defaultClient.getUserProfile(credentials, context))
        if(profile.isExpired())
          AnonymousJWTProfile
        else
          PanakeiaJWTProfile(defaultClient.getUserProfile(credentials, context))
      } catch {
        case _: Exception =>
          // We can throw only TransportException.
          // Otherwise exception will be sent to the client with stack trace.
          AnonymousJWTProfile
      }
      serviceCall(profile)
    }

  /**
   * Service call composition for authorization.
   *
   * @param authorizer Authorizer (may be composite)
   * @param serviceCall Service call
   * @tparam Request Type of request
   * @tparam Response Type of response
   * @return Service call with authorization logic
   */
  def authorize[Request, Response](authorizer: Authorizer[PanakeiaJWTProfile])(serviceCall: PanakeiaJWTProfile => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] =
    authorize(securityConfig.getClients.getDefaultSecurityClients, authorizer, serviceCall)

  /**
   * Service call composition for authorization.
   *
   * @param clientName Name of authentication client
   * @param authorizer Authorizer (may be composite)
   * @param serviceCall Service call
   * @tparam Request Type of request
   * @tparam Response Type of response
   * @return Service call with authorization logic
   */
  def authorize[Request, Response](clientName: String,
                                   authorizer: Authorizer[PanakeiaJWTProfile],
                                   serviceCall: PanakeiaJWTProfile => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] =
    authenticate(clientName, (profile: PanakeiaJWTProfile) => ServerServiceCall.compose { requestHeader =>
      val authorized = try {
        authorizer != null && authorizer.isAuthorized(new LagomWebContext(requestHeader), singletonList(profile)) && !profile.isExpired
      } catch {
        case _: Exception =>
          // We can throw only TransportException.
          // Otherwise exception will be sent to the client with stack trace.
          false
      }
      if (!authorized) throw Forbidden("Authorization failed")
      serviceCall.apply(profile)
    })

  /**
   * Service call composition for authorization.
   *
   * @param authorizerName Name of authorizer, registered in security config
   * @param serviceCall    Service call
   * @tparam Request Type of request
   * @tparam Response Type of response
   * @return Service call with authorization logic
   */
  def authorize[Request, Response](authorizerName: String,
                                   serviceCall: PanakeiaJWTProfile => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] =
    authorize(securityConfig.getAuthorizers.get(authorizerName).asInstanceOf[Authorizer[PanakeiaJWTProfile]])(serviceCall)


  //  def authorize[Request, Response](authorizerName: String)(serviceCall: PanakeiaJWTProfile => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] = {
  //    authorize(securityConfig.getAuthorizers.get(authorizerName).asInstanceOf[Authorizer[PanakeiaJWTProfile]])(serviceCall)
  //  }

  /**
   * Service call composition for authorization.
   *
   * @param clientName     Name of authentication client
   * @param authorizerName Name of authorizer, registered in security config
   * @param serviceCall    Service call
   * @tparam Request Type of request
   * @tparam Response Type of response
   * @return Service call with authorization logic
   */
  def authorize[Request, Response](clientName: String,
                                   authorizerName: String)(serviceCall: PanakeiaJWTProfile => ServerServiceCall[Request, Response]): ServerServiceCall[Request, Response] =
    authorize(clientName, securityConfig.getAuthorizers.get(authorizerName).asInstanceOf[Authorizer[PanakeiaJWTProfile]], serviceCall)

}
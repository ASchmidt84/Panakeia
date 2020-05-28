package de.heilpraktikerelbmarsch.security.api.services

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.{Method, NotFound}
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import de.heilpraktikerelbmarsch.security.api.adt.requests.CreateProfileRequest
import de.heilpraktikerelbmarsch.util.adt.security.MicroServiceIdentifier.SecurityServiceIdentifier
import io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import io.swagger.v3.oas.annotations.info.{Contact, Info, License}
import io.swagger.v3.oas.annotations.media.{ArraySchema, Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.servers.Server
import io.swagger.v3.oas.annotations.{OpenAPIDefinition, Operation, Parameter}
import org.taymyr.lagom.scaladsl.openapi.{LagomError, OpenAPIService}

trait SecurityService extends Service with OpenAPIService {

  /**
   * Verifies that you are the person who you say you are - (signIn)
   * The servicecall takes the password or key or anything what can confirm that you are you!
   * @param username The user id who you say you are!
   * @return
   */
  def jwtAuthenticate(username: String): ServiceCall[String,String]

  /**
   * Sends a new JWT with an extended expire date
   * @return JWT String with extended expire date
   */
  def jwtRegenerate(username: String): ServiceCall[NotUsed,String]

  /**
   * Adds a role to the given user
   * @param username
   * @return
   */
  def addRole(username: String): ServiceCall[String,NotUsed]

  /**
   * removes a role from a given user
   * @param username
   * @return
   */
  def removeRole(username: String): ServiceCall[String,NotUsed]

  /**
   * Creates an user
   * @return
   */
  def createUser(): ServiceCall[CreateProfileRequest,String]

  override final def descriptor: Descriptor = {
    import Service._
    named(SecurityServiceIdentifier.name).withCalls(
      restCall(Method.POST,path(":username"), jwtAuthenticate _),
      pathCall(path(":username/regenerate"),jwtRegenerate _),
      restCall(Method.PUT,path(":username/role"), addRole _),
      restCall(Method.DELETE,path(":username/role"), removeRole _)
    ).withAutoAcl(true)
  }

  private def path(x: String) = s"/api/${SecurityServiceIdentifier.path}/$x"

}

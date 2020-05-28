package de.heilpraktikerelbmarsch.security.api.components

import com.sksamuel.elastic4s.{ElasticClient, ElasticNodeEndpoint, ElasticProperties}
import com.sksamuel.elastic4s.http.JavaClient
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.security.api.profiles.PanakeiaJWTProfile
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.RestClientBuilder.{HttpClientConfigCallback, RequestConfigCallback}
import org.pac4j.core.context.HttpConstants.{AUTHORIZATION_HEADER, BEARER_HEADER_PREFIX}
import org.pac4j.core.context.WebContext
import org.pac4j.core.profile.CommonProfile
import org.pac4j.http.client.direct.HeaderClient
import org.pac4j.lagom.jwt.JwtAuthenticatorHelper

trait SecurityComponents {


  def headerClient(implicit c: Config): HeaderClient = {
    val headerClient = new HeaderClient
    headerClient.setHeaderName(AUTHORIZATION_HEADER)
    headerClient.setPrefixHeader(BEARER_HEADER_PREFIX)
    // Strongly recommendation use `JwtAuthenticatorHelper` for initializing `JwtAuthenticator`.
    headerClient.setAuthenticator(JwtAuthenticatorHelper.parse(c.getConfig("pac4j.lagom.jwt.authenticator")))
    // Custom AuthorizationGenerator to compute the appropriate roles of the authenticated user profile.
    // Roles are fetched from JWT 'roles' attribute.
    // See more http://www.pac4j.org/3.4.x/docs/clients.html#2-compute-roles-and-permissions
    headerClient.setAuthorizationGenerator( (_: WebContext, profile: CommonProfile) => {
      val pr = PanakeiaJWTProfile(profile)
      if (pr.containsAttribute("roles")) pr.addRoles(profile.getAttribute("roles", classOf[java.util.Collection[String]]))
      pr
    })

    headerClient.setName("jwt_header")
    headerClient
  }

  /**
   * PAC4J client.
   * See <a href="http://www.pac4j.org/3.4.x/docs/clients.html">PAC4J documentation</a> for more information about PAC4J clients.
   * https://github.com/pac4j/lagom-pac4j
   * https://github.com/pac4j/lagom-pac4j-scala-demo/blob/master/impl/src/main/scala/org/pac4j/lagom/demo/impl/AuthJwtLoader.scala
   */
  val jwtClient: HeaderClient

  lazy val serviceConfig: org.pac4j.core.config.Config = {
    val config = new org.pac4j.core.config.Config(jwtClient)
    config.getClients.setDefaultSecurityClients(jwtClient.getName)
    config
  }

  def config: Config

  protected lazy val provider: BasicCredentialsProvider = {
    val provider = new BasicCredentialsProvider
    val credentials = new UsernamePasswordCredentials(
      config.getString("elasticsearch.config.username"),
      config.getString("elasticsearch.config.password")
    )
    provider.setCredentials(AuthScope.ANY, credentials)
    provider
  }

  import org.apache.http.client.config.RequestConfig.Builder
  protected lazy val requestCallback: RequestConfigCallback = (requestConfigBuilder: Builder) => {
    requestConfigBuilder
  }
  protected lazy val httpCallback: HttpClientConfigCallback = (httpClientBuilder: HttpAsyncClientBuilder) => {
    httpClientBuilder.setDefaultCredentialsProvider(provider)
  }

  import net.ceedubs.ficus.Ficus._

  protected lazy val elasticClient: ElasticClient = ElasticClient(JavaClient(
    ElasticProperties(config.as[Seq[Config]]("elasticsearch.config.seq").map{u =>
      ElasticNodeEndpoint(
        u.as[String]("protocol"),
        u.as[String]("host"),
        u.as[Int]("port"),
        Option(u.as[String]("prefix")).filter(_.trim.nonEmpty)
      )
    }),requestCallback,httpCallback
  ))

}

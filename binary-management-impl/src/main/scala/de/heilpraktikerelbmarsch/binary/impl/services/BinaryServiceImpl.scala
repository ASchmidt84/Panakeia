package de.heilpraktikerelbmarsch.binary.impl.services

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.binary.api.services.BinaryService
import de.heilpraktikerelbmarsch.security.api.services.SystemSecuredService

import scala.concurrent.{ExecutionContext, Future}

class BinaryServiceImpl(override val securityConfig: org.pac4j.core.config.Config)(implicit ec: ExecutionContext, val config: Config) extends BinaryService with SystemSecuredService {

  override def createBinaryEntry(): ServiceCall[NotUsed, NotUsed] = authenticate{profile => ServerServiceCall{_ => Future.successful(NotUsed)}}

}

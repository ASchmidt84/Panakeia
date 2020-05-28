package de.heilpraktikerelbmarsch.benefitRate.impl.services

import java.util.UUID

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.benefitRate.api.request.{BenefitRateForm, RateForm}
import de.heilpraktikerelbmarsch.benefitRate.api.response.BenefitRateView
import de.heilpraktikerelbmarsch.benefitRate.api.services.BenefitRateService
import de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates.BenefitRate
import de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates.BenefitRate.Command
import de.heilpraktikerelbmarsch.security.api.services.SystemSecuredService

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class BenefitRateServiceImpl(persistentEntityRegistry: PersistentEntityRegistry,
                             override val securityConfig: org.pac4j.core.config.Config,
                             clusterSharding: ClusterSharding)(implicit ec: ExecutionContext, val config: Config) extends BenefitRateService with SystemSecuredService {

  implicit val timeout = Timeout(15.seconds)

  private def benefitRateEntity(id: UUID): EntityRef[Command] = clusterSharding.entityRefFor(BenefitRate.typedKey,id.toString)

  override def createBenefitRate(): ServiceCall[BenefitRateForm, BenefitRateView] = ???

  override def getBenefitRate(number: String): ServiceCall[NotUsed, BenefitRateView] = ???

  override def setRate(number: String): ServiceCall[RateForm, BenefitRateView] = ???

  override def deleteBenefitRate(number: String): ServiceCall[NotUsed, BenefitRateView] = ???

  override def size(): ServiceCall[NotUsed, Long] = ???

  override def list(take: Long, drop: Long): ServiceCall[NotUsed, Seq[BenefitRateView]] = ???

  override def search(query: String, take: Long, drop: Long): ServiceCall[NotUsed, Seq[BenefitRateView]] = ???
}

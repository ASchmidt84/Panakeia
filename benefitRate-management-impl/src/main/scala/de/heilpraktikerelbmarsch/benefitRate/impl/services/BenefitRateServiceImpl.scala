package de.heilpraktikerelbmarsch.benefitRate.impl.services

import java.util.UUID

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.{BadRequest, Forbidden, NotFound}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.benefitRate.api.adt.Rate
import de.heilpraktikerelbmarsch.benefitRate.api.request.{BenefitRateForm, RateForm}
import de.heilpraktikerelbmarsch.benefitRate.api.response.BenefitRateView
import de.heilpraktikerelbmarsch.benefitRate.api.services.BenefitRateService
import de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates.{BenefitRate, BenefitRateRepository}
import de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates.BenefitRate.{Command, CommandAccepted, CommandRejected, Confirmation, Summary}
import de.heilpraktikerelbmarsch.security.api.profiles.PanakeiaJWTProfile
import de.heilpraktikerelbmarsch.security.api.services.SystemSecuredService
import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles
import squants.market.EUR

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
 * BenefiteRate Service is an service to provide all payable handlings/actions for the therapist to the patient.
 * This means if the therapist executes like give an injection, the rate which the patient has to pay,
 * has to lay here. And will be stored in his file.
 * @param persistentEntityRegistry
 * @param securityConfig
 * @param clusterSharding
 * @param repository
 * @param ec
 * @param config
 */
class BenefitRateServiceImpl(persistentEntityRegistry: PersistentEntityRegistry,
                             override val securityConfig: org.pac4j.core.config.Config,
                             clusterSharding: ClusterSharding,
                             repository: BenefitRateRepository)(implicit ec: ExecutionContext, val config: Config) extends BenefitRateService with SystemSecuredService {

  import PanakeiaJWTProfile._

  private val allAccessRoles = DefaultRoles.praxisRoles:::DefaultRoles.adminRoles:::DefaultRoles.systemRoles

  implicit val timeout = Timeout(15.seconds)

  private def entity(id: UUID): EntityRef[Command] = clusterSharding.entityRefFor(BenefitRate.typedKey,id.toString)
  private def idToView(id: UUID): Future[BenefitRateView] = entity(id).ask[Confirmation](reply => BenefitRate.Get(reply) ).map(r => r)
  private implicit def toView(x: Summary): BenefitRateView = BenefitRateView(
    x.number, x.isOfficialGebueH, x.alternativeForGOAE,
    x.description,x.rates
  )
  private implicit def replyToView(x: Confirmation): BenefitRateView = x match {
    case CommandAccepted(summary) => summary
    case CommandRejected(error) => throw BadRequest(error)
  }

  override def createBenefitRate(): ServiceCall[BenefitRateForm, BenefitRateView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{data =>
    repository.findByNumber(data.feeNumber).flatMap{
      case Some(_) =>
        throw BadRequest(s"The benefit with ${data.feeNumber} already exists")
      case _ =>
        val newId = UUID.randomUUID()
        entity(newId)
          .ask[Confirmation]( reply => BenefitRate.CreateBenefitRate(
            data.feeNumber.trim,data.benefitDescription,data.officialGebUEH,data.alternativeForGOAE,reply,profile
          ))
        .map(r => r)
    }
  }}

  override def getBenefitRate(number: String): ServiceCall[NotUsed, BenefitRateView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{data =>
    repository.findByNumber(number.trim).flatMap{
      case Some(id) =>
        entity(id)
          .ask[Confirmation](reply => BenefitRate.Get(reply))
          .map(r => r)
      case _ => throw NotFound(s"A benefit rate with number $number was not found")
    }
  }}

  override def setRate(number: String): ServiceCall[RateForm, BenefitRateView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{rate =>
    repository.findByNumber(number.trim).flatMap{
      case Some(id) =>
        entity(id)
          .ask[Confirmation](reply => BenefitRate.SetRate(Rate( EUR(rate.euro), rate.settlementType ), reply, profile) )
          .map(r => r)
      case _ => throw NotFound(s"The BenefitRate with number $number was not found")
    }
  }}

  override def deleteBenefitRate(number: String): ServiceCall[String, Boolean] = authorize(requireAnyRole(DefaultRoles.adminRoles)){profile => ServerServiceCall{reason =>
    repository.findByNumber(number.trim).flatMap{
      case None =>
        throw NotFound(s"The requested benefit rate with number $number was not found")
      case Some(id) =>
        entity(id).ask[Confirmation](reply => BenefitRate.Get(reply)).flatMap{
          case CommandAccepted(summary) if summary.active =>
            entity(id)
              .ask[Confirmation](r => BenefitRate.Delete(reason,r,profile) )
              .map(_ => true)
          case CommandRejected(d) => throw Forbidden(d)
        }
    }
  }}

  override def size(active: Option[Boolean]): ServiceCall[NotUsed, Long] = authorize(requireAnyRole(allAccessRoles)){_ => ServerServiceCall{_ =>
    repository.length(active).map(_.toLong)
  }}

  override def list(take: Int,
                    drop: Int): ServiceCall[NotUsed, Seq[BenefitRateView]] = authorize(requireAnyRole(allAccessRoles)){_ => ServerServiceCall{_ =>
    repository.list(take,drop).flatMap{i =>
      Future.sequence( i.map( idToView ) )
    }
  }}

  override def search(query: String,
                      take: Long,
                      drop: Long): ServiceCall[NotUsed, Seq[BenefitRateView]] = authorize(requireAnyRole(allAccessRoles)){_ => ServerServiceCall{_ =>
    repository.search(query).flatMap{i =>
      Future.sequence(i.map(idToView))
    }
  }}
}

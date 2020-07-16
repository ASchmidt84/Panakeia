package de.heilpraktikerelbmarsch.file.impl.services

import java.util.UUID

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.{BadRequest, NotFound}
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.file.api.adt.{BinaryEntry, PatientFileView, TextEntry}
import de.heilpraktikerelbmarsch.file.api.requests.{BinaryEntryForm, TextEntryForm}
import de.heilpraktikerelbmarsch.file.api.services.FileService
import de.heilpraktikerelbmarsch.file.impl.file.PatientFile.{Command, CommandAccepted, CommandRejected, Confirmation, Summary}
import de.heilpraktikerelbmarsch.file.impl.file.{PatientFile, PatientFileRepository}
import de.heilpraktikerelbmarsch.security.api.services.SystemSecuredService
import de.heilpraktikerelbmarsch.security.api.profiles.PanakeiaJWTProfile
import de.heilpraktikerelbmarsch.util.adt.contacts.Operator
import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, postfixOps}

class FileServiceImpl(override val securityConfig: org.pac4j.core.config.Config,
                      clusterSharding: ClusterSharding,
                      repository: PatientFileRepository)(implicit ec: ExecutionContext, val config: Config) extends FileService with SystemSecuredService {

  import PanakeiaJWTProfile._
  import Operator._

  private val allAccessRoles = DefaultRoles.praxisRoles:::DefaultRoles.adminRoles:::DefaultRoles.systemRoles

  implicit val timeout: Timeout = Timeout(15 seconds)

  private def entity(id: UUID): EntityRef[Command] = clusterSharding.entityRefFor(PatientFile.typedKey,id.toString)

  private def idToView(id: UUID): Future[PatientFileView] = entity(id).ask[Confirmation](reply => PatientFile.Get(reply)).map(replyToView).recover{
    case _ => throw NotFound("Entity was not found, sorry")
  }

  private implicit def toView(summary: Summary): PatientFileView = PatientFileView(
    summary.patient, summary.status, summary.entries
  )

  private implicit def replyToView(x: Confirmation): PatientFileView = x match {
    case CommandAccepted(summary) => toView(summary)
    case CommandRejected(error) => throw BadRequest(error)
  }

  private def numberToEntity(number: String): Future[EntityRef[Command]] = {
    repository.getByPatientNumber(number).map( t => entity(t) ).recover{
      case _ => throw NotFound(s"Patient with $number was not found")
    }
  }

  private def numberToView(number: String): Future[PatientFileView] = {
    repository.getByPatientNumber(number).flatMap(t => idToView(t))
      .recover(_ => throw NotFound(s"Patient with number: $number was not found"))
  }



  override def getFile(patientNumber: String): ServiceCall[NotUsed, PatientFileView] = authorize(requireAnyRole(DefaultRoles.praxisRoles)){profile => ServerServiceCall{_ =>
    numberToView(patientNumber.trim)
  }}

  override def addBinaryEntry(patientNumber: String): ServiceCall[BinaryEntryForm, PatientFileView] = authorize(requireAnyRole(DefaultRoles.praxisRoles)){profile => ServerServiceCall{data =>
    numberToEntity(patientNumber.trim).flatMap{entity =>
      entity
        .ask[Confirmation](r => PatientFile.AddEntry( BinaryEntry(DateTime.now(), profile, data.kind, data.text, data.fileId, data.fileName, data.benefitRateNumber ), profile, r ) )
        .map(replyToView)
    }.recover{
      case _ => throw NotFound("Patient file was not found")
    }
  }}

  override def addTextEntry(patientNumber: String): ServiceCall[TextEntryForm, PatientFileView] = authorize(requireAnyRole(DefaultRoles.praxisRoles)){profile => ServerServiceCall{data =>
    numberToEntity(patientNumber.trim).flatMap{entity =>
      entity
        .ask[Confirmation](r => PatientFile.AddEntry( TextEntry(DateTime.now(), profile, data.kind, data.text, data.benefitRateNumber ), profile, r ) )
        .map(replyToView)
    }.recover{
      case _ => throw NotFound("Patient file was not found")
    }
  }}
}

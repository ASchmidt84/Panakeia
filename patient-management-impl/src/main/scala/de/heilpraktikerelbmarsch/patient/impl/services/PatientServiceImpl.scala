package de.heilpraktikerelbmarsch.patient.impl.services

import java.util.UUID

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.{BadRequest, NotFound}
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.patient.api.adt.{PatientPicture, PatientStatus, PatientView}
import de.heilpraktikerelbmarsch.patient.api.request.{CreatePatientForm, PatientPhoneForm, PatientSearchForm}
import de.heilpraktikerelbmarsch.patient.api.services.PatientService
import de.heilpraktikerelbmarsch.patient.impl.patient.{Patient, PatientRepository}
import de.heilpraktikerelbmarsch.patient.impl.patient.Patient.{Command, CommandAccepted, CommandRejected, Confirmation, Summary}
import de.heilpraktikerelbmarsch.security.api.profiles.PanakeiaJWTProfile
import de.heilpraktikerelbmarsch.security.api.services.SystemSecuredService
import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, Operator, PersonalData, PostalAddress}
import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Random

class PatientServiceImpl(override val securityConfig: org.pac4j.core.config.Config,
                         persistentEntityRegistry: PersistentEntityRegistry,
                         clusterSharding: ClusterSharding,
                         repo: PatientRepository)(implicit ec: ExecutionContext, val config: Config) extends PatientService with SystemSecuredService {

  import PanakeiaJWTProfile._
  import Operator._

  private val allAccessRoles = DefaultRoles.praxisRoles:::DefaultRoles.adminRoles:::DefaultRoles.systemRoles

  implicit val timeout = Timeout(15.seconds)

  private def entity(id: UUID): EntityRef[Command] = clusterSharding.entityRefFor(Patient.typedKey,id.toString)

  private def idToView(id: UUID): Future[PatientView] = entity(id).ask[Confirmation](reply => Patient.Get(reply)).map(replyToView).recover{
    case _ => throw NotFound("Entity was not found, sorry")
  }

  private implicit def toView(summary: Summary): PatientView = PatientView(
    summary.number,
    summary.personalData, summary.postalAddress, summary.birthdate,
    summary.email, summary.status, summary.phonePrivate,
    summary.phoneWork, summary.cellPhone, summary.fax,
    summary.job,
    summary.personalPicture
  )

  private implicit def replyToView(x: Confirmation): PatientView = x match {
    case CommandAccepted(summary) => toView(summary)
    case CommandRejected(error) => throw BadRequest(error)
  }

  private def numberToEntity(number: String): Future[EntityRef[Command]] = {
    repo.getPatientByNumber(number).map( t => entity(t) ).recover{
      case _ => throw NotFound(s"Patient with $number was not found")
    }
  }

  private def numberToView(number: String): Future[PatientView] = {
    repo.getPatientByNumber(number).flatMap(t => idToView(t))
      .recover(_ => throw NotFound(s"Patient with number: $number was not found"))
  }

  /**
   * Creates a new patient
   *
   * @return a [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]
   */
  override def createPatient(): ServiceCall[CreatePatientForm, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{data =>
    val number = data.number.getOrElse( List.fill(8)(Random.nextInt(9).abs.toString).mkString )
    numberToEntity(number).map{
      throw BadRequest(s"Patient with number $number already exists!")
    }.recoverWith{
      case _: NotFound =>
        val id = UUID.randomUUID()
        entity(id).ask[Confirmation](reply => Patient.Create(
          number, data.status, data.personalData, data.postalAddress, data.birthdate,
          data.email, data.phonePrivate, data.phoneWork, data.cellPhone,
          data.fax, data.job, profile, reply
        ) ).map(r => r)
    }
  }}

  /**
   * Get a patient by his patient number which is unique!
   *
   * @param number patient number unique
   *
   * @return returns a [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]] in case of found
   *         otherwise a [[com.lightbend.lagom.scaladsl.api.transport.NotFound]]
   */
  override def getPatientByPatientNumber(number: String): ServiceCall[NotUsed, PatientView] = authorize(requireAnyRole(allAccessRoles)){_ => ServerServiceCall{_ =>
    numberToView(number.trim)
  }}

  /**
   * Searches for patients
   *
   * @param take maximum number of results
   * @param drop drops the first n results
   *
   * @return a sequence of [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]. Size 0 if nothing was found
   */
  override def searchPatient(take: Int, drop: Int): ServiceCall[PatientSearchForm, Seq[PatientView]] = authorize(requireAnyRole(allAccessRoles)){_ => ServerServiceCall{search =>
    val nameFut = search.name.map(u => repo.searchByName(u.trim) ).getOrElse(Future.successful(Nil))
    val emailFut = search.email.map(u => repo.searchByEmail(u.trim) ).getOrElse(Future.successful(Nil))
    val streetFut = search.street.map(u => repo.searchByStreet(u.trim) ).getOrElse(Future.successful(Nil))
    val zipFut = search.zip.map(u => repo.searchByPostalCode(u.trim) ).getOrElse(Future.successful(Nil))
    val placeFut = search.place.map(u => repo.searchByCity(u.trim) ).getOrElse(Future.successful(Nil))
    for{
      names <- nameFut
      emails <- emailFut
      streets <- streetFut
      zips <- zipFut
      places <- placeFut
      views <- Future.sequence((names ++ emails ++ streets ++ zips ++ places).distinct.slice(drop, drop + take).map(idToView))
    } yield {
      views
    }
  }}

  /**
   * Counts all patient by this status
   *
   * @param status status which the patient must have
   *
   * @return [[Int]] represents the counted patients in database
   */
  override def length(status: PatientStatus): ServiceCall[NotUsed, Int] = authorize(requireAnyRole(allAccessRoles)){_ => ServerServiceCall{_ =>
    repo.count(Some(status))
  }}

  /**
   * Lists all patient with defined status
   *
   * @param take   The maximum size for this list
   * @param drop   drops this number at the front of the founded list
   * @param status the defined status which a patient must have
   *
   * @return a sequence of [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]. Size 0 if nothing was found
   */
  override def listPatient(take: Int, drop: Int, status: PatientStatus): ServiceCall[NotUsed, Seq[PatientView]] = authorize(requireAnyRole(allAccessRoles)){_ => ServerServiceCall{_ =>
    repo.list(status,take,drop).flatMap(seq => Future.sequence(seq.map(idToView)))
  }}

  /**
   * Changes the status of an patient
   *
   * @param number required number to change the patient status
   *
   * @return [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]
   */
  override def changePatientStatus(number: String): ServiceCall[PatientStatus, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{status =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ChangeStatus(status,profile,reply)).map(replyToView)
    }
  }}

  /**
   * Changes the postal address of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientPostalAddress(number: String): ServiceCall[PostalAddress, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{postal =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ChangePostalAddress(postal,profile,reply)).map(replyToView)
    }
  }}

  /**
   * Changes the personal data of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientPersonalData(number: String): ServiceCall[PersonalData, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{personal =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ChangePersonalData(personal,profile,reply)).map(replyToView)
    }
  }}

  /**
   * Changes the email of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientEmailAddress(number: String): ServiceCall[EmailAddress, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{mail =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ChangeEmail(Some(mail),profile,reply)).map(replyToView)
    }
  }}

  /**
   * Changes the job of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientJob(number: String): ServiceCall[String, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{job =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ChangeJob(Some(job),profile,reply)).map(replyToView)
    }
  }}

  /**
   * Changes the phone data of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientPhoneData(number: String): ServiceCall[PatientPhoneForm, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{phone =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ChangePhoneData(phone.phonePrivate,phone.phoneWork,phone.cellPhone,phone.fax,profile,reply) ).map(replyToView)
    }
  }}

  /**
   * Clears the job of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def clearPatientJob(number: String): ServiceCall[NotUsed, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{_ =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ChangeJob(None,profile,reply)).map(replyToView)
    }
  }}

  /**
   * Clears the email address of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def clearPatientEmailAddress(number: String): ServiceCall[NotUsed, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{_ =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ChangeEmail(None,profile,reply)).map(replyToView)
    }
  }}

  /**
   * Adds a picture to patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def setPatientPicture(number: String): ServiceCall[PatientPicture, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{picture =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.SetPicture(picture.id,picture.pictureName,profile,reply) ).map(replyToView)
    }
  }}

  /**
   * Removes the picture of a patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def clearPatientPicture(number: String): ServiceCall[NotUsed, PatientView] = authorize(requireAnyRole(allAccessRoles)){profile => ServerServiceCall{_ =>
    numberToEntity(number).flatMap{
      _.ask[Confirmation](reply => Patient.ClearPicture(profile,reply)).map(replyToView)
    }
  }}

  //https://github.com/lagom/lagom-samples/blob/1.6.x/shopping-cart/shopping-cart-scala/shopping-cart/src/main/scala/com/example/shoppingcart/impl/ShoppingCartServiceImpl.scala
  override def createdTopic: Topic[PatientView] = TopicProducer.taggedStreamWithOffset(Patient.Event.Tag) { (tag, fromOffset) =>
    persistentEntityRegistry
      .eventStream(tag,fromOffset)
      .filter(_.event.isInstanceOf[Patient.Created])
      .mapAsync(1) {
        case EventStreamElement(id, _, offset) =>
          idToView(UUID.fromString(id)).map{r =>
            r -> offset
          }
      }
  }

  override def deletedTopic: Topic[String] = TopicProducer.taggedStreamWithOffset(Patient.Event.Tag){ (tag, fromOffset) =>
    persistentEntityRegistry
      .eventStream(tag,fromOffset)
      .filter(_.event.isInstanceOf[Patient.Deleted])
      .mapAsync(1){
        case EventStreamElement(_,event,off) =>
          Future.successful(event.asInstanceOf[Patient.Deleted].number -> off)
      }
  }

  override def statusChangedTopic: Topic[PatientView] = TopicProducer.taggedStreamWithOffset(Patient.Event.Tag){ (tag, fromOffset) =>
    persistentEntityRegistry
      .eventStream(tag,fromOffset)
      .filter(_.event.isInstanceOf[Patient.StatusChanged])
      .mapAsync(1){
        case EventStreamElement(id,_,off) =>
          idToView(UUID.fromString(id))
            .map(r => r -> off)
      }
  }
}

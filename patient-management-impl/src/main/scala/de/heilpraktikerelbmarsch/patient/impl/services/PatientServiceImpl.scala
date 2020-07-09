package de.heilpraktikerelbmarsch.patient.impl.services

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.typesafe.config.Config
import de.heilpraktikerelbmarsch.patient.api.adt.{PatientStatus, PatientView}
import de.heilpraktikerelbmarsch.patient.api.request.{PatientPhoneForm, PatientSearchForm}
import de.heilpraktikerelbmarsch.patient.api.services.PatientService
import de.heilpraktikerelbmarsch.security.api.profiles.PanakeiaJWTProfile
import de.heilpraktikerelbmarsch.security.api.services.SystemSecuredService
import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData, PostalAddress}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class PatientServiceImpl(override val securityConfig: org.pac4j.core.config.Config,
                         clusterSharding: ClusterSharding)(implicit ec: ExecutionContext, val config: Config) extends PatientService with SystemSecuredService {

  import PanakeiaJWTProfile._

  implicit val timeout = Timeout(15.seconds)

  /**
   * Creates a new patient
   *
   * @return a [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]
   */
  override def createPatient(): ServiceCall[NotUsed, PatientView] = ???

  /**
   * Get a patient by his patient number which is unique!
   *
   * @param number patient number unique
   *
   * @return returns a [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]] in case of found
   *         otherwise a [[com.lightbend.lagom.scaladsl.api.transport.NotFound]]
   */
  override def getPatientByPatientNumber(number: String): ServiceCall[NotUsed, PatientView] = ???

  /**
   * Searches for patients
   *
   * @param take maximum number of results
   * @param drop drops the first n results
   *
   * @return a sequence of [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]. Size 0 if nothing was found
   */
  override def searchPatient(take: Int, drop: Int): ServiceCall[PatientSearchForm, Seq[PatientView]] = ???

  /**
   * Counts all patient by this status
   *
   * @param status status which the patient must have
   *
   * @return [[Int]] represents the counted patients in database
   */
  override def length(status: PatientStatus): ServiceCall[NotUsed, Int] = ???

  /**
   * Lists all patient with defined status
   *
   * @param take   The maximum size for this list
   * @param drop   drops this number at the front of the founded list
   * @param status the defined status which a patient must have
   *
   * @return a sequence of [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]. Size 0 if nothing was found
   */
  override def listPatient(take: Int, drop: Int, status: PatientStatus): ServiceCall[NotUsed, Seq[PatientView]] = ???

  /**
   * Changes the status of an patient
   *
   * @param number required number to change the patient status
   *
   * @return [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]
   */
  override def changePatientStatus(number: String): ServiceCall[PatientStatus, PatientView] = ???

  /**
   * Changes the postal address of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientPostalAddress(number: String): ServiceCall[PostalAddress, PatientView] = ???

  /**
   * Changes the personal data of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientPersonalData(number: String): ServiceCall[PersonalData, PatientView] = ???

  /**
   * Changes the email of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientEmailAddress(number: String): ServiceCall[EmailAddress, PatientView] = ???

  /**
   * Changes the job of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientJob(number: String): ServiceCall[String, PatientView] = ???

  /**
   * Changes the phone data of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def changePatientPhoneData(number: String): ServiceCall[PatientPhoneForm, PatientView] = ???

  /**
   * Clears the job of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def clearPatientJob(number: String): ServiceCall[NotUsed, PatientView] = ???

  /**
   * Clears the email address of an patient
   *
   * @param number required number to change the patient data
   *
   * @return
   */
  override def clearPatientEmailAddress(number: String): ServiceCall[NotUsed, PatientView] = ???
}

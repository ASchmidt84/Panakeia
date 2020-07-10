package de.heilpraktikerelbmarsch.patient.api.services

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer
import com.lightbend.lagom.scaladsl.api.transport.{Method, NotFound}
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceAcl, ServiceCall}
import de.heilpraktikerelbmarsch.patient.api.adt.{PatientPicture, PatientStatus, PatientView}
import de.heilpraktikerelbmarsch.patient.api.request.{CreatePatientForm, PatientPhoneForm, PatientSearchForm}
import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData, PostalAddress}
import de.heilpraktikerelbmarsch.util.adt.security.MicroServiceIdentifier.PatientServiceIdentifier

import scala.collection.immutable

trait PatientService extends Service {

  import PatientStatus._

  /**
   * Creates a new patient
   * @return a [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]
   */
  def createPatient(): ServiceCall[CreatePatientForm,PatientView]

  /**
   * Get a patient by his patient number which is unique!
   * @param number patient number unique
   * @return returns a [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]] in case of found
   *         otherwise a [[com.lightbend.lagom.scaladsl.api.transport.NotFound]]
   */
  def getPatientByPatientNumber(number: String): ServiceCall[NotUsed,PatientView]

  /**
   * Searches for patients
   * @param take maximum number of results
   * @param drop drops the first n results
   * @return a sequence of [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]. Size 0 if nothing was found
   */
  def searchPatient(take: Int, drop: Int): ServiceCall[PatientSearchForm,Seq[PatientView]]

  /**
   * Counts all patient by this status
   * @param status status which the patient must have
   * @return [[Int]] represents the counted patients in database
   */
  def length(status: PatientStatus): ServiceCall[NotUsed,Int]


  /**
   * Lists all patient with defined status
   * @param take The maximum size for this list
   * @param drop drops this number at the front of the founded list
   * @param status the defined status which a patient must have
   * @return a sequence of [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]. Size 0 if nothing was found
   */
  def listPatient(take: Int, drop: Int, status: PatientStatus): ServiceCall[NotUsed,Seq[PatientView]]

  /**
   * Changes the status of an patient
   * @param number required number to change the patient status
   * @return [[de.heilpraktikerelbmarsch.patient.api.adt.PatientView]]
   */
  def changePatientStatus(number: String): ServiceCall[PatientStatus,PatientView]

  /**
   * Changes the postal address of an patient
   * @param number required number to change the patient data
   * @return
   */
  def changePatientPostalAddress(number: String): ServiceCall[PostalAddress,PatientView]

  /**
   * Changes the personal data of an patient
   * @param number required number to change the patient data
   * @return
   */
  def changePatientPersonalData(number: String): ServiceCall[PersonalData,PatientView]

  /**
   * Changes the email of an patient
   * @param number required number to change the patient data
   * @return
   */
  def changePatientEmailAddress(number: String): ServiceCall[EmailAddress,PatientView]

  /**
   * Changes the job of an patient
   * @param number required number to change the patient data
   * @return
   */
  def changePatientJob(number: String): ServiceCall[String,PatientView]

  /**
   * Changes the phone data of an patient
   * @param number required number to change the patient data
   * @return
   */
  def changePatientPhoneData(number: String): ServiceCall[PatientPhoneForm,PatientView]

  /**
   * Clears the job of an patient
   * @param number required number to change the patient data
   * @return
   */
  def clearPatientJob(number: String): ServiceCall[NotUsed,PatientView]

  /**
   * Clears the email address of an patient
   * @param number required number to change the patient data
   * @return
   */
  def clearPatientEmailAddress(number: String): ServiceCall[NotUsed,PatientView]

  /**
   * Adds a picture to patient
   * @param number required number to change the patient data
   * @return
   */
  def setPatientPicture(number: String): ServiceCall[PatientPicture,PatientView]

  /**
   * Removes the picture of a patient
   * @param number required number to change the patient data
   * @return
   */
  def clearPatientPicture(number: String): ServiceCall[NotUsed,PatientView]


  implicit val pathParamSerializerPatientStatus: PathParamSerializer[PatientStatus] = new PathParamSerializer[PatientStatus] {
    override def serialize(parameter: PatientStatus): Seq[String] = immutable.Seq(parameter.name)

    override def deserialize(parameters: Seq[String]): PatientStatus = parameters.headOption match {
      case Some(i) => PatientStatus.withName(i)
      case _ => throw new IllegalArgumentException("Patientstatus needs a parameter as string")
    }
  }


  override final def descriptor: Descriptor = {
    import Service._
    named(PatientServiceIdentifier.name)
      .withCalls(
        restCall(Method.POST, path("search?take&drop"), searchPatient _),
        restCall(Method.GET, path("length?status"), length _),
        restCall(Method.GET, path("list?take&drop&status"), listPatient _),
        restCall(Method.POST, path("search?take&drop"), searchPatient _),
        restCall(Method.GET, path(":number"), getPatientByPatientNumber _),
        restCall(Method.PUT,path(":number/status"), changePatientStatus _),
        restCall(Method.PUT,path(":number/postal-address"), changePatientPostalAddress _),
        restCall(Method.PUT,path(":number/personal-data"), changePatientPersonalData _),
        restCall(Method.PUT,path(":number/email"), changePatientEmailAddress _),
        restCall(Method.DELETE,path(":number/email"), clearPatientEmailAddress _),
        restCall(Method.PUT,path(":number/job"), changePatientJob _),
        restCall(Method.DELETE,path(":number/job"), clearPatientJob _),
        restCall(Method.PUT,path(":number/phone-data"), changePatientPhoneData _),
        restCall(Method.PUT,path(":number/picture"), setPatientPicture _),
        restCall(Method.DELETE,path(":number/picture"), clearPatientPicture _),
        restCall(Method.POST,path(""), createPatient _)
      )
      .withAutoAcl(true)
  }

  private def path(x: String) = s"/api/${PatientServiceIdentifier.path}/$x"

}

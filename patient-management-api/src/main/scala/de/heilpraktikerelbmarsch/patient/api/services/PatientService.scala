package de.heilpraktikerelbmarsch.patient.api.services

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.{Method, NotFound}
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceAcl, ServiceCall}
import de.heilpraktikerelbmarsch.patient.api.request.PatientSearchForm
import de.heilpraktikerelbmarsch.patient.api.response.PatientView
import de.heilpraktikerelbmarsch.util.adt.security.MicroServiceIdentifier.PatientServiceIdentifier

trait PatientService extends Service {

  /**
   * Creates a new patient
   * @return a [[de.heilpraktikerelbmarsch.patient.api.response.PatientView]]
   */
  def createPatient(): ServiceCall[NotUsed,PatientView]

  /**
   * Get a patient by his patient number which is unique!
   * @param number patient number unique
   * @return returns a [[de.heilpraktikerelbmarsch.patient.api.response.PatientView]] in case of found
   *         otherwise a [[com.lightbend.lagom.scaladsl.api.transport.NotFound]]
   */
  def getPatientByPatientNumber(number: String): ServiceCall[NotUsed,PatientView]

  /**
   * Searches for patients
   * @param take maximum number of results
   * @param drop drops the first n results
   * @return a sequence of [[de.heilpraktikerelbmarsch.patient.api.response.PatientView]]. Size 0 if nothing was found
   */
  def searchPatient(take: Int, drop: Int): ServiceCall[PatientSearchForm,Seq[PatientView]]


  override final def descriptor: Descriptor = {
    import Service._
    named(PatientServiceIdentifier.name)
      .withCalls(
        restCall(Method.POST, path("search?take&drop"), searchPatient _),
        restCall(Method.GET, path("?number"), getPatientByPatientNumber _),
        restCall(Method.POST,path(""), createPatient _)
      )
      .withAutoAcl(true)
  }

  private def path(x: String) = s"/api/${PatientServiceIdentifier.path}/$x"

}

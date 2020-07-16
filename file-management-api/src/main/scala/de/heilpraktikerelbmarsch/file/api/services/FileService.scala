package de.heilpraktikerelbmarsch.file.api.services

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import de.heilpraktikerelbmarsch.file.api.adt.{FileStatus, PatientFileView}
import de.heilpraktikerelbmarsch.file.api.requests.{BinaryEntryForm, TextEntryForm}
import de.heilpraktikerelbmarsch.util.adt.security.MicroServiceIdentifier.PatientFileServiceIdentifier

trait FileService extends Service {

  import FileStatus._

  def getFile(patientNumber: String): ServiceCall[NotUsed,PatientFileView]

  def addBinaryEntry(patientNumber: String): ServiceCall[BinaryEntryForm, PatientFileView]

  def addTextEntry(patientNumber: String): ServiceCall[TextEntryForm, PatientFileView]


  override final def descriptor: Descriptor = {
    import Service._
    named(PatientFileServiceIdentifier.name)
      .withCalls(
        restCall(Method.GET,path("patient/:patientNumber"), getFile _),
        restCall(Method.PUT,path("patient/:patientNumber/entry/text"), addTextEntry _),
        restCall(Method.PUT,path("patient/:patientNumber/entry/binary"), addBinaryEntry _)
      )
      .withAutoAcl(true)
  }

  private def path(x: String) = s"/api/${PatientFileServiceIdentifier.path}/$x"

}

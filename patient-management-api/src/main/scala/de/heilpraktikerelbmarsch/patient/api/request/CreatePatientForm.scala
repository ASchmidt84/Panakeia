package de.heilpraktikerelbmarsch.patient.api.request

import de.heilpraktikerelbmarsch.patient.api.adt.PatientStatus
import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData, PhoneNumber, PostalAddress}
import org.joda.time.LocalDate
import play.api.libs.json.{Json, OFormat}

case class CreatePatientForm(number: Option[String],
                             status: PatientStatus,
                             personalData: PersonalData,
                             postalAddress: Option[PostalAddress],
                             birthdate: Option[LocalDate],
                             email: Option[EmailAddress],
                             phonePrivate: Option[PhoneNumber],
                             phoneWork: Option[PhoneNumber],
                             cellPhone: Option[PhoneNumber],
                             fax: Option[PhoneNumber],
                             job: Option[String])

object CreatePatientForm {
  import PostalAddress._
  import PhoneNumber._
  import PatientStatus._
  import EmailAddress._
  import PersonalData._
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._

  implicit val format: OFormat[CreatePatientForm] = Json.format

}
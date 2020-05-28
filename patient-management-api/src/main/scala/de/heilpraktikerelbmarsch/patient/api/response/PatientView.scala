package de.heilpraktikerelbmarsch.patient.api.response


import de.heilpraktikerelbmarsch.patient.api.adt.PatientStatus
import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData, PhoneNumber, PostalAddress}
import org.joda.time.LocalDate
import play.api.libs.json.{Json, OFormat}

case class PatientView(personalData: PersonalData,
                       postalAddress: Option[PostalAddress],
                       birthdate: Option[LocalDate],
                       email: Option[EmailAddress],
                       status: PatientStatus,
                       phonePrivate: Option[PhoneNumber],
                       phoneWork: Option[PhoneNumber],
                       cellPhone: Option[PhoneNumber],
                       fax: Option[PhoneNumber],
                       job: Option[String])

object PatientView {
  import PhoneNumber._
  import PostalAddress._
  import EmailAddress._
  import PatientStatus._
  import PersonalData._
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._
  implicit val format: OFormat[PatientView] = Json.format

}

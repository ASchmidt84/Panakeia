package de.heilpraktikerelbmarsch.patient.api.adt

import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData, PhoneNumber, PostalAddress}
import org.joda.time.LocalDate
import play.api.libs.json.{Json, OFormat}

case class PatientView(number: String,
                       personalData: PersonalData,
                       postalAddress: Option[PostalAddress],
                       birthdate: Option[LocalDate],
                       email: Option[EmailAddress],
                       status: PatientStatus,
                       phonePrivate: Option[PhoneNumber],
                       phoneWork: Option[PhoneNumber],
                       cellPhone: Option[PhoneNumber],
                       fax: Option[PhoneNumber],
                       job: Option[String],
                       personalPicture: Option[PatientPicture])

object PatientView {
  import PatientPicture._
  import PersonalData._
  import PostalAddress._
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._
  import PhoneNumber._
  import EmailAddress._
  import PatientStatus._
  implicit val format: OFormat[PatientView] = Json.format

}

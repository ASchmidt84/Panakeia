package de.heilpraktikerelbmarsch.file.api.adt

import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData, PostalAddress}
import org.joda.time.LocalDate
import play.api.libs.json.{Json, OFormat}

import EmailAddress._
import PersonalData._
import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._
import PostalAddress._

final case class PatientView(number: String,
                             personalData: PersonalData,
                             emailAddress: Option[EmailAddress],
                             birthdate: Option[LocalDate],
                             postalAddress: PostalAddress)

object PatientView {
  implicit val format: OFormat[PatientView] = Json.format
}

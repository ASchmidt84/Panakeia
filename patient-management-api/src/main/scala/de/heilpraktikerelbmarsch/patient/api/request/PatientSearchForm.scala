package de.heilpraktikerelbmarsch.patient.api.request

import de.heilpraktikerelbmarsch.util.adt.contacts.PhoneNumber
import play.api.libs.json.{Json, OFormat}

case class PatientSearchForm(name: Option[String],
                             email: Option[String],
                             street: Option[String],
                             zip: Option[String],
                             place: Option[String],
                             birthday: Option[String])

object PatientSearchForm {
  implicit val format: OFormat[PatientSearchForm] = Json.format
}


case class PatientPhoneForm(phonePrivate: Option[PhoneNumber],
                            phoneWork: Option[PhoneNumber],
                            cellPhone: Option[PhoneNumber],
                            fax: Option[PhoneNumber])

object PatientPhoneForm {
  import PhoneNumber._

  implicit val format: OFormat[PatientPhoneForm] = Json.format

}
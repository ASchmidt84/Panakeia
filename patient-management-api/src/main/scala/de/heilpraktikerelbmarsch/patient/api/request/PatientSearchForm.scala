package de.heilpraktikerelbmarsch.patient.api.request

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

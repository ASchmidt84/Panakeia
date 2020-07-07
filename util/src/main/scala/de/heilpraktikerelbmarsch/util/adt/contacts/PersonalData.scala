package de.heilpraktikerelbmarsch.util.adt.contacts

import play.api.libs.json.{Json, OFormat}

case class PersonalData(salutation: String,
                        firstName: String,
                        lastName: String) {

  override def toString: String = s"$salutation $firstName $lastName"

}

object PersonalData {
  implicit val format: OFormat[PersonalData] = Json.format
}
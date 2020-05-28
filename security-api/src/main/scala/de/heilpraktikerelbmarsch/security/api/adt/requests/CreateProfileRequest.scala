package de.heilpraktikerelbmarsch.security.api.adt.requests

import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData}
import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles
import play.api.libs.json.{Json, OFormat}

case class CreateProfileRequest(userName: String,
                                password: String,
                                email: EmailAddress,
                                roles: Seq[DefaultRoles],
                                personalData: PersonalData)

object CreateProfileRequest {
  import EmailAddress._
  import DefaultRoles._
  import PersonalData._
  implicit val format: OFormat[CreateProfileRequest] = Json.format
}

package de.heilpraktikerelbmarsch.util.adt.contacts

import play.api.libs.json.{Format, Json}

/**
 * Generic Address format!
 *
 * @param addressLine1 All what should be in the top line! Name, title
 * @param addressLine2 Means for example Company, C/O or anything other
 * @param streetLine Streetline means all what identifies the street or the post box
 * @param postalCode postal code is the zip code for the city/state
 * @param city city should explains self
 * @param state state is optional and is important for international shipping
 * @param countryLine The destination country
 */
case class PostalAddress(addressLine1: String,
                         addressLine2: Option[String],
                         streetLine: String,
                         postalCode: String,
                         city: String,
                         state: Option[String],
                         countryLine: Option[Country])

object PostalAddress {
  import Country._
  implicit val format: Format[PostalAddress] = Json.format
}
package de.heilpraktikerelbmarsch.util.adt.contacts

import java.util.Locale

import play.api.libs.json.{Format, Json}

/**
* Country
* @param isoCode
*/
case class Country(isoCode: String) {
  import Country._

  override def toString = {
    val f: Locale = this
    f.getDisplayCountry
  }

}

object Country {
  implicit def toCountry(x: Locale): Country = Country(x.getCountry)
  implicit def toLocale(x: Country): Locale = new Locale("",x.isoCode)
  implicit val format: Format[Country] = Json.format[Country]
}



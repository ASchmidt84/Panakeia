package de.heilpraktikerelbmarsch.util.adt.contacts

import com.google.i18n.phonenumbers._
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.Phonenumber.{PhoneNumber => GooglePhoneNumber}
import play.api.libs.json.{Json, OFormat}

/**
 * A Phonenumber
 *
 * @param countryCode The country code like 49 for Germany
 * @param nationalNumber The complete national number like 405152530
 */
case class PhoneNumber(countryCode: Int,
                       nationalNumber: Long) {

  private val phoneUtil = PhoneNumberUtil.getInstance()
  private val number = new GooglePhoneNumber().setCountryCode(countryCode).setNationalNumber(nationalNumber)

  val nationalString: String = phoneUtil.format(number, PhoneNumberFormat.NATIONAL)
  val internationalString: String = phoneUtil.format(number, PhoneNumberFormat.INTERNATIONAL)
  val e164String: String = phoneUtil.format(number, PhoneNumberFormat.E164)

  /**
   * Produces the national representation of phone number
   * @return Exp. "044 668 18 00"
   */
  override def toString: String = nationalString

  /**
   * String output of phonenumber
   * @param internationalOrE164 Either the international string or the E164
   * @return International: "+41 44 668 18 00" E164: "+41446681800"
   */
  def toString(internationalOrE164: Boolean): String = if(internationalOrE164) internationalString else e164String

}


object PhoneNumber {
  implicit val format: OFormat[PhoneNumber] = Json.format
}
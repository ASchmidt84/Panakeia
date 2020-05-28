package de.heilpraktikerelbmarsch.util.adt.contacts

import scala.language.implicitConversions

//https://github.com/hmrc/emailaddress geklaut

case class EmailAddress(value: String) extends StringValue {

  val (mailbox, domain): (EmailAddress.Mailbox, EmailAddress.Domain) = value match {
    case EmailAddress.validEmail(m, d) => (EmailAddress.Mailbox(m), EmailAddress.Domain(d))
    case invalidEmail => throw new IllegalArgumentException(s"'$invalidEmail' is not a valid email address")
  }

  lazy val obfuscated: ObfuscatedEmailAddress = ObfuscatedEmailAddress.apply(value)
}

object EmailAddress {
  final private[contacts] val validDomain = """^([a-zA-Z0-9-\[]+(?:\.[a-zA-Z0-9-\]]+)*)$""".r
  final private[contacts] val validEmail = """^((?!\.)(?!.*?\.\.)[a-zA-Z0-9.!#$%&’'"*+/=?^_`{|}~-]*[a-zA-Z0-9!#$%&’'"*+/=?^_`{|}~-]+)@((?!-)[a-zA-Z0-9-.\[]+\.[a-zA-Z0-9-.\]]+)$""".r

  def isValid(email: String): Boolean = email match {
    case validEmail(_,_) => true
    case invalidEmail => false
  }

  case class Mailbox private[EmailAddress] (value: String) extends StringValue
  case class Domain(value: String) extends StringValue {
    value match {
      case EmailAddress.validDomain(_) => //
      case invalidDomain => throw new IllegalArgumentException(s"'$invalidDomain' is not a valid email domain")
    }
  }

  import play.api.libs.json._

  implicit val emailAddressReads: Reads[EmailAddress] = new Reads[EmailAddress] {
    def reads(js: JsValue): JsResult[EmailAddress] = js.validate[String].flatMap {
      case s if EmailAddress.isValid(s) => JsSuccess(EmailAddress(s))
      case s => JsError("not a valid email address")
    }
  }
  implicit val emailAddressWrites: Writes[EmailAddress] = new Writes[EmailAddress] {
    def writes(e: EmailAddress): JsValue = JsString(e.value)
  }

  implicit val format: Format[EmailAddress] = Format(emailAddressReads,emailAddressWrites)


}

object StringValue {
  implicit def stringValueToString(e: StringValue): String = e.value
}

trait StringValue {
  def value: String
  override def toString: String = value
}

trait ObfuscatedEmailAddress {
  val value: String
  override def toString: String = value
}

object ObfuscatedEmailAddress {
  final private val shortMailbox = "(.{1,2})".r
  final private val longMailbox = "(.)(.*)(.)".r

  import EmailAddress.validEmail

  implicit def obfuscatedEmailToString(e: ObfuscatedEmailAddress): String = e.value

  def apply(plainEmailAddress: String): ObfuscatedEmailAddress = new ObfuscatedEmailAddress {
    val value: String = plainEmailAddress match {
      case validEmail(shortMailbox(m), domain) =>
        s"${obscure(m)}@$domain"

      case validEmail(longMailbox(firstLetter,middle,lastLetter), domain) =>
        s"$firstLetter${obscure(middle)}$lastLetter@$domain"

      case invalidEmail =>
        throw new IllegalArgumentException(s"Cannot obfuscate invalid email address '$invalidEmail'")
    }
  }

  private def obscure(text: String) = "*" * text.length
}

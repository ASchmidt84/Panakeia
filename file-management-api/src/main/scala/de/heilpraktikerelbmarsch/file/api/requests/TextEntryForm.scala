package de.heilpraktikerelbmarsch.file.api.requests

import de.heilpraktikerelbmarsch.file.api.adt.EntryTyp
import play.api.libs.json.{Json, OFormat}
import EntryTyp._

case class TextEntryForm(kind:EntryTyp,
                         text: String,
                         benefitRateNumber: Option[String])

object TextEntryForm {
  implicit val form: OFormat[TextEntryForm] = Json.format
}

final case class BinaryEntryForm(kind:EntryTyp,
                                 text: Option[String],
                                 fileId: String,
                                 fileName: String,
                                 benefitRateNumber: Option[String])

object BinaryEntryForm {
  implicit val format: OFormat[BinaryEntryForm] = Json.format
}
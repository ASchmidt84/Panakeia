package de.heilpraktikerelbmarsch.file.impl.file.adt

import de.heilpraktikerelbmarsch.file.api.adt.EntryTyp
import de.heilpraktikerelbmarsch.util.adt.contacts.Operator
import julienrf.json.derived
import org.joda.time.DateTime
import play.api.libs.json.OFormat

sealed trait Entry{
  def timestamp: DateTime
  def operator: Operator
  def kind: EntryTyp
  def benefitRateNumber: Option[String]
}

object Entry {
  implicit val format: OFormat[Entry] = derived.oformat()
}

final case class TextEntry(timestamp: DateTime,
                           operator: Operator,
                           kind:EntryTyp,
                           text: String,
                           benefitRateNumber: Option[String]) extends Entry

object TextEntry {
  implicit val format: OFormat[TextEntry] = derived.oformat()
}

final case class BinaryEntry(timestamp: DateTime,
                             operator: Operator,
                             kind:EntryTyp,
                             text: Option[String],
                             fileId: String,
                             fileName: String,
                             benefitRateNumber: Option[String]) extends Entry

object BinaryEntry {
  implicit val format: OFormat[BinaryEntry] = derived.oformat()
}

final case class LaborReportEntry(timestamp: DateTime,
                                  operator: Operator,
                                  kind:EntryTyp,
                                  text: Option[String],
                                  reportNumber: String,
                                  laborNumber: String,
                                  laborValues: Seq[LaborValue],
                                  laborBinaryId: Option[String],
                                  laborBinaryName: Option[String],
                                  benefitRateNumber: Option[String]) extends Entry

object LaborReportEntry {
  import LaborValue._
  implicit val format: OFormat[LaborReportEntry] = derived.oformat()
}

final case class LaborValue(name: String,
                            value: String,
                            normalValue: Option[String])

object LaborValue {
  implicit val format: OFormat[LaborValue] = derived.oformat()
}
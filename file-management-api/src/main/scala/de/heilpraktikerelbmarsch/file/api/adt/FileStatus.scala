package de.heilpraktikerelbmarsch.file.api.adt

import enumeratum._
import play.api.libs.json.OFormat
import julienrf.json.derived

sealed abstract class FileStatus private[adt](val name: String) extends EnumEntry {
  override def toString: String = name.trim.toLowerCase
}


object FileStatus extends Enum[FileStatus] {
  override def values: IndexedSeq[FileStatus] = findValues

  case object Init extends FileStatus("init")
  case object Active extends FileStatus("active")
  case object Closed extends FileStatus("closed")

  implicit val format: OFormat[FileStatus] = derived.oformat()

}


sealed abstract class EntryTyp private[adt](val name: String) extends EnumEntry {
  override def toString: String = name.trim.toLowerCase

  case object Anamnesis extends EntryTyp("anamnesis")
  case object Diagnose extends EntryTyp("diagnose")
  case object LaborReport extends EntryTyp("labor-report")
  case object Diagnostic extends EntryTyp("diagnostic")
  case object Information extends EntryTyp("information")

  implicit val format: OFormat[EntryTyp] = derived.oformat()

}
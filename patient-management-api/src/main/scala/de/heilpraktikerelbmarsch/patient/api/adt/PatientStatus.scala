package de.heilpraktikerelbmarsch.patient.api.adt

import java.util.UUID

import enumeratum._
import play.api.libs.json.{Json, OFormat}
import julienrf.json.derived
import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._

sealed abstract class PatientStatus private[PatientStatus](val name: String) extends EnumEntry {
  override def toString: String = name
}

object PatientStatus extends Enum[PatientStatus] {
  override def values: IndexedSeq[PatientStatus] = findValues

  case object Active extends PatientStatus("active")
  case object InActive extends PatientStatus("inactive") //Ist nicht mehr aktiv was auch immer das heißen mag
  case object Deceased extends PatientStatus("deceased") //verstorben
  case object Abandoned extends PatientStatus("abandoned") //Praxis verlassen

  implicit val format: OFormat[PatientStatus] = derived.oformat()

}

final case class PatientPicture(id: UUID, pictureName: String)

object PatientPicture {
  implicit val format: OFormat[PatientPicture] = Json.format
}
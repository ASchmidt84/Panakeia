package de.heilpraktikerelbmarsch.patient.api.adt

import enumeratum._
import play.api.libs.json.OFormat
import julienrf.json.derived

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
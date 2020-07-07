package de.heilpraktikerelbmarsch.util.adt.benefits

import enumeratum._
import julienrf.json.derived
import play.api.libs.json.OFormat

sealed abstract class SettlementType private[SettlementType](val name: String,
                                                             val description: String,
                                                             val group: String) extends EnumEntry {
  override def toString: String = name
}

object SettlementType extends Enum[SettlementType] {

  override def values: IndexedSeq[SettlementType] = findValues

  //https://www.heilpraktikersoftware-blog.de/so-finden-heilpraktikerinnen-und-heilpraktiker-die-richtige-abrechnungsart-fuer-die-abrechnung-mit-dem-gebueh/
  case object SZPauschal extends SettlementType("SZ Pauschal", "Selbstzahler pauschal", "GKV")
  case object SZGebueH extends SettlementType("SZ GebüH", "Selbstzahler nach GebüH", "GKV")
  case object Zusatz extends SettlementType("Zusatz", "Selbstzahler zusatzversichert", "GKV")
  case object Osteopathie extends SettlementType("Osteopathie","Gebühren nach Osteopathie Gebühren der GKV","GKV")
  case object PKV1 extends SettlementType("PKV1","Schwellenwert der GebüH/GOÄ","PKV")
  case object PKV2 extends SettlementType("PKV2","Mindestsatz der GebüH/GOÄ","PKV")
  case object MaxRahmen extends SettlementType("Max. Rahmen", "Höchstsatz also maximaler Rahmen", "PKV")
  case object MinRahmen extends SettlementType("Min. Rahmen","Niedrigster Satz also minimalster Rahmen","PKV")
  case object PBKK extends SettlementType("PostBeaKK","Post-Beamter","Beihilfe")
  case object Bundeshilfe extends SettlementType("Beihilfe Bund", "Bundesbeamter","Beihilfe")
  case object BeihilfeBW extends SettlementType("Landes-Beihilfe Baden-Württenberg","Landesbeamter","Beihilfe")
  case object BeihilfeBY extends SettlementType("Landes-Beihilfe Bayern","Landesbeamter","Beihilfe")
  case object BeihilfeBE extends SettlementType("Landes-Beihilfe Berlin","Landesbeamter","Beihilfe")
  case object BeihilfeBB extends SettlementType("Landes-Beihilfe Brandenburg","Landesbeamter","Beihilfe")
  case object BeihilfeHB extends SettlementType("Landes-Beihilfe Bremen","Landesbeamter","Beihilfe")
  case object BeihilfeHH extends SettlementType("Landes-Beihilfe Hamburg","Landesbeamter","Beihilfe")
  case object BeihilfeHE extends SettlementType("Landes-Beihilfe Hessen","Landesbeamter","Beihilfe")
  case object BeihilfeMV extends SettlementType("Landes-Beihilfe Mecklenburg-Vorpommern","Landesbeamter","Beihilfe")
  case object BeihilfeNI extends SettlementType("Landes-Beihilfe Niedersachsen","Landesbeamter","Beihilfe")
  case object BeihilfeNRW extends SettlementType("Landes-Beihilfe Nordrhein-Westfalen","Landesbeamter","Beihilfe")
  case object BeihilfeRP extends SettlementType("Landes-Beihilfe Rheinland-Pfalz","Landesbeamter","Beihilfe")
  case object BeihilfeSL extends SettlementType("Landes-Beihilfe Saarland","Landesbeamter","Beihilfe")
  case object BeihilfeSN extends SettlementType("Landes-Beihilfe Sachsen","Landesbeamter","Beihilfe")
  case object BeihilfeST extends SettlementType("Landes-Beihilfe Sachsen-Anhalt","Landesbeamter","Beihilfe")
  case object BeihilfeSH extends SettlementType("Landes-Beihilfe Schleswig-Holstein","Landesbeamter","Beihilfe")
  case object BeihilfeTH extends SettlementType("Landes-Beihilfe Thüringen","Landesbeamter","Beihilfe")
  case object Selbstzahler1 extends SettlementType("Selbstzahler 1", "Selbstzahler eigene Definition", "Alle")
  case object Selbstzahler2 extends SettlementType("Selbstzahler 2", "Selbstzahler eigene Definition", "Alle")
  case object Selbstzahler3 extends SettlementType("Selbstzahler 3", "Selbstzahler eigene Definition", "Alle")

  implicit val format: OFormat[SettlementType] = derived.oformat()

}

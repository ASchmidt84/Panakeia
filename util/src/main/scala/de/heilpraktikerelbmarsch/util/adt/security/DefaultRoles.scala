package de.heilpraktikerelbmarsch.util.adt.security

import enumeratum.{Enum, EnumEntry}
import julienrf.json.derived
import play.api.libs.json.OFormat

sealed abstract class DefaultRoles private[adt](val name: String) extends EnumEntry {
  override def toString: String = s"Role: ${name.trim.toLowerCase}"
}

object DefaultRoles extends Enum[DefaultRoles] {
  override def values: IndexedSeq[DefaultRoles] = findValues

  val systemRoles = List(SystemRole)
  val adminRoles = List(SuperAdminRole,AdminRole)
  val praxisRoles = List(TherapistRole,AssistantRole)
  val outsideRoles = List(PatientRole,ExternalRole)

  case object SystemRole extends DefaultRoles("system") //System intern
  case object SuperAdminRole extends DefaultRoles("super-admin") //Technischer Admin
  case object AdminRole extends DefaultRoles("admin") //Administrator
  case object TherapistRole extends DefaultRoles("therapist") //Behandler/Therapeut
  case object AssistantRole extends DefaultRoles("assistant") // Assistenten
  case object PatientRole extends DefaultRoles("patient") // Patient
  case object ExternalRole extends DefaultRoles("external") // Jemand der von extern kommt

  implicit val format: OFormat[DefaultRoles] = derived.oformat[DefaultRoles]()
}


sealed abstract class MicroServiceIdentifier private[adt](val name: String, val path: String) extends EnumEntry {
  override def toString: String = name
}

object MicroServiceIdentifier extends Enum[MicroServiceIdentifier] {
  override def values: IndexedSeq[MicroServiceIdentifier] = findValues

  case object SecurityServiceIdentifier extends MicroServiceIdentifier("security-service","service/security")
  case object BinaryServiceIdentifier extends MicroServiceIdentifier("binary-service","service/binary")
  case object PatientServiceIdentifier extends MicroServiceIdentifier("patient-service","service/patient")
  case object BenefitRateServiceIdentifier extends MicroServiceIdentifier("benefiterate-service","service/benefit-rate")

  implicit val format: OFormat[MicroServiceIdentifier] = derived.oformat[MicroServiceIdentifier]()
}
package de.heilpraktikerelbmarsch.file.api.adt

import play.api.libs.json.{Json, OFormat}

case class PatientFileView(patientView: PatientView,
                           status: FileStatus,
                           entries: Seq[Entry])

object PatientFileView {
  import PatientView._
  import FileStatus._
  import Entry._

  implicit val format: OFormat[PatientFileView] = Json.format
}
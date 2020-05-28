package de.heilpraktikerelbmarsch.binary.api.adt.request


case class PatientDataUploadForm(patientNumber: String,
                                 caseNumber: Option[String],
                                 description: Option[String])
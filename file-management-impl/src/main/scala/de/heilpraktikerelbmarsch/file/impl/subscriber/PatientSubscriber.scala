package de.heilpraktikerelbmarsch.file.impl.subscriber

import java.util.UUID

import akka.Done
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import de.heilpraktikerelbmarsch.file.impl.file.{PatientFile, PatientFileProcessor, PatientFileRepository}
import de.heilpraktikerelbmarsch.file.api.adt.{PatientView => FilePatientView}
import de.heilpraktikerelbmarsch.file.impl.file.PatientFile.Confirmation
import de.heilpraktikerelbmarsch.patient.api.adt.PatientStatus
import de.heilpraktikerelbmarsch.patient.api.services.PatientService
import de.heilpraktikerelbmarsch.util.adt.contacts.{PostalAddress, SystemOperator}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class PatientSubscriber(service: PatientService,
                        clusterSharding: ClusterSharding,
                        repository: PatientFileRepository)(implicit ec: ExecutionContext) {

  implicit val timeout = Timeout(30.seconds)

  service.createdTopic
    .subscribe
    .atLeastOnce(
      Flow.fromFunction(r => createFile(r) )
    )

  service.deletedTopic
    .subscribe
    .atLeastOnce(
      Flow.fromFunction(r => closeFile(r))
    )

  service.statusChangedTopic
    .subscribe
    .atLeastOnce(
      Flow.fromFunction(d => statusChanged(d) )
    )

  private def statusChanged(x: de.heilpraktikerelbmarsch.patient.api.adt.PatientView): Done = {
    val fut = repository.getByPatientNumber(x.number).flatMap{id =>
      x.status match {
        case PatientStatus.Active => //
          clusterSharding
            .entityRefFor(PatientFile.typedKey,id.toString)
            .ask[Confirmation](r => PatientFile.Reopen(SystemOperator("PatientSubscriber"), s"${x.number} changed status to ${x.status.toString}", r ) )
            .map(_ => Done)
            .recover(_ => Done)
        case _ => //Schließen
          clusterSharding
            .entityRefFor(PatientFile.typedKey,id.toString)
            .ask[Confirmation](r => PatientFile.Close(SystemOperator("PatientSubscriber"), s"${x.number} changed status to ${x.status.toString} and was closed", r ) )
            .map(_ => Done)
            .recover(_ => Done)
      }
    }.recover(_ => Done)
    Await.result(fut,timeout.duration)
  }

  private def createFile(x: de.heilpraktikerelbmarsch.patient.api.adt.PatientView): Done = {
    val view = FilePatientView(x.number,x.personalData,x.email,x.birthdate,x.postalAddress.getOrElse(PostalAddress("LEER",None,"LEER","12345","LEER",None,None)))

    val fut = repository.getByPatientNumber(x.number).map{_ => Done}.recoverWith{
      case _ =>
        clusterSharding
          .entityRefFor(PatientFile.typedKey,UUID.randomUUID().toString)
          .ask[Confirmation](r => PatientFile.Create(view,SystemOperator("PatientSubscriber"),r) )
          .map(_ => Done)
    }

    Await.result(fut,timeout.duration)
  }


  private def closeFile(patientNumber: String): Done = {
    val fut = repository.getByPatientNumber(patientNumber).flatMap{id =>
      clusterSharding
        .entityRefFor(PatientFile.typedKey,id.toString)
        .ask[Confirmation](r => PatientFile.Close(SystemOperator("PatientSubscriber"), s"$patientNumber was closed", r ) )
        .map(_ => Done)
    }.recover(_ => Done)

    Await.result(fut,timeout.duration)
  }


}

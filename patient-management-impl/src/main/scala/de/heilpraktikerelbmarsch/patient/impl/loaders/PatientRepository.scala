package de.heilpraktikerelbmarsch.patient.impl.loaders


import java.util.UUID

import de.heilpraktikerelbmarsch.patient.api.adt.PatientStatus
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class PatientRepository(database: slick.jdbc.JdbcBackend.Database,
                        processor: PatientProcessor)(implicit ec: ExecutionContext) {

  def getPatientByNumber(number: String): Future[UUID] = database.run(
    processor
      .table
      .filter(_.number === number)
      .map(_.entityId).result
      .head
  ).recover(_ => throw new RuntimeException("Not found"))


  def count(status: Option[PatientStatus]): Future[Int] = {
    import processor.statusScalar
    val query = status.map(r => processor.table.filter(i => i.status === r) ).getOrElse(processor.table)
    database.run( query.length.result )
  }

  def list(take: Int, drop: Int): Future[Seq[UUID]] = database.run(
    processor.table.sortBy(_.number.asc).take(take).drop(drop).map(_.entityId).result
  )

}

package de.heilpraktikerelbmarsch.file.impl.file

import java.util.UUID

import de.heilpraktikerelbmarsch.file.api.adt.FileStatus

import scala.concurrent.{ExecutionContext, Future}

class PatientFileRepository(database: slick.jdbc.JdbcBackend.Database,
                            processor: PatientFileProcessor)(implicit ec: ExecutionContext) {

  import slick.jdbc.PostgresProfile.api._
  import processor._

  def count(status: Option[FileStatus]): Future[Int] = {
    database.run( table.filterIf(status.isDefined)(r => r.status === status.get).length.result )
  }

  def getByPatientNumber(number: String): Future[UUID] = database.run(
    table.filter(_.number === number).map(_.actorId).take(1).result.head
  )

  def list(take: Int, drop: Int): Future[Seq[UUID]] = database.run(
    table.sortBy(_.number.toLowerCase.asc).map(_.actorId).take(take).drop(drop).result
  )


}

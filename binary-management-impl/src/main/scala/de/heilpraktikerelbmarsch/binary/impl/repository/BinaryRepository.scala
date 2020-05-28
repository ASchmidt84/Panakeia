package de.heilpraktikerelbmarsch.binary.impl.repository

import java.io.File
import java.util.UUID

import akka.Done
import de.heilpraktikerelbmarsch.binary.impl.storage.Storage
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class BinaryRepository(database: Database,
                       storage: Storage)(implicit ec: ExecutionContext) {
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._

  class BinaryTable(tag: Tag) extends Table[BinaryTableData](tag,"binary") {
    def id = column[UUID]("id",O.PrimaryKey)
    def bucketName = column[String]("bucket_name")
    def fileName = column[String]("file_name")
    def version = column[Int]("version")
    def description = column[Option[String]]("description")

    def * = (id,bucketName,version,fileName,description) <> ( (BinaryTableData.apply _).tupled, BinaryTableData.unapply )
  }

  val binaryTable = TableQuery[BinaryTable]

  def createTable() = binaryTable.schema.createIfNotExists

  createTable() //Bauen!

  def addPatientFile(patientNumber: String,
                     caseNumber: Option[String],
                     file: File,
                     shortDescription: Option[String]): Future[UUID] = {
    val uuid = UUID.randomUUID()
    storage.savePatientFile(patientNumber,caseNumber,file).flatMap{ tuple =>
      database
        .run(binaryTable += BinaryTableData(uuid,tuple._1,1,tuple._2,shortDescription))
        .map{_ => uuid}
    }
  }

}


final case class BinaryTableData(id: UUID,
                                 bucketName: String,
                                 version: Int,
                                 fileName: String,
                                 shortDescription: Option[String])
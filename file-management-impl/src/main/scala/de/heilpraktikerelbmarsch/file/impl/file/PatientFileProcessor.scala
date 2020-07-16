package de.heilpraktikerelbmarsch.file.impl.file

import java.util.UUID

import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, ReadSideProcessor}
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import slick.jdbc.PostgresProfile.api._
import PatientFile._
import akka.Done
import de.heilpraktikerelbmarsch.file.api.adt.FileStatus
import de.heilpraktikerelbmarsch.util.adt.contacts.EmailAddress
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

import scala.concurrent.ExecutionContext

class PatientFileProcessor(readSide: SlickReadSide)(implicit ec: ExecutionContext) extends ReadSideProcessor[Event] {

  private val tableName = "patient_file_processor"

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[Event] = readSide
    .builder[Event](tableName+"_offset")
    .setGlobalPrepare(table.schema.createIfNotExists)
    .setEventHandler[Created](r => insert(r.event.patientView.number,UUID.fromString(r.entityId), r.event.patientView.personalData.firstName, r.event.patientView.personalData.lastName,r.event.patientView.emailAddress ) )
    .setEventHandler[Reopened](r => changeStatus( UUID.fromString(r.entityId), FileStatus.Open ) )
    .setEventHandler[Closed](r => changeStatus( UUID.fromString(r.entityId), FileStatus.Closed ) )
    .build()

  override def aggregateTags: Set[AggregateEventTag[Event]] = Event.Tag.allTags


  //-------- Methods ---------------------

  private def changeStatus(entityId: UUID, status: FileStatus): DBIO[Done] = {
    table.filter(_.actorId === entityId).map(r => r.status).update(status)
  }.map(_ => Done)

  private def insert(number: String, entityId: UUID, firstName: String, surname: String, email: Option[EmailAddress]): DBIO[Done] = {
    table += PatientFileTableData(number.trim,FileStatus.Open,entityId,firstName,surname,email)
  }.map(_ => Done)

  //-------- Methods ---------------------

  implicit val statusScalar: JdbcType[FileStatus] with BaseTypedType[FileStatus] = MappedColumnType.base[FileStatus,String](
    {b => b.name },
    {b => FileStatus.withName(b) }
  )

  implicit val emailAddressScalar: JdbcType[EmailAddress] with BaseTypedType[EmailAddress] = MappedColumnType.base[EmailAddress,String](
    {b => b.value },
    {b => EmailAddress(b) }
  )

  case class PatientFileTableData(patientNumber: String,
                                  status: FileStatus,
                                  actorId: UUID,
                                  firstName: String,
                                  surname: String,
                                  emailAddress: Option[EmailAddress])

  class PatientFileTable(tag: Tag) extends Table[PatientFileTableData](tag,tableName) {

    def number = column[String]("patient_number",O.Unique)
    def status = column[FileStatus]("status")
    def actorId = column[UUID]("actor_uuid")
    def firstName = column[String]("first_name")
    def surname = column[String]("surname")
    def email = column[Option[EmailAddress]]("email")

    def * = (number,status,actorId,firstName,surname,email) <> (PatientFileTableData.tupled, PatientFileTableData.unapply )

  }


  val table = TableQuery[PatientFileTable]

}

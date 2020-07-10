package de.heilpraktikerelbmarsch.patient.impl.patient

import java.util.UUID

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}
import de.heilpraktikerelbmarsch.patient.api.adt.PatientStatus
import de.heilpraktikerelbmarsch.util.adt.contacts.EmailAddress
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

class PatientProcessor(readSide: SlickReadSide)(implicit ec: ExecutionContext) extends ReadSideProcessor[Patient.Event] {

  import Patient._

  private val tableName = "patient_processor"

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[Patient.Event] = readSide
    .builder[Patient.Event](tableName+"_offset")
    .setGlobalPrepare(table.schema.createIfNotExists)
    .setEventHandler[Created](insert)
    .setEventHandler[StatusChanged](updateStatus)
    .setEventHandler[PersonalDataChanged](updatePersonalData)
    .setEventHandler[EmailChanged](updateEmail)
    .setEventHandler[PostalAddressChanged](updatePostalAddress)
    .setEventHandler[Deleted](deletePatient)
    .build()

  override def aggregateTags: Set[AggregateEventTag[Patient.Event]] = Patient.Event.Tag.allTags

  //-------- Methods ----------------------

  private def singleFilterQuery(id: String) = table.filter(_.entityId === UUID.fromString(id))

  private def deletePatient(e: EventStreamElement[Deleted]): DBIO[Done] = {
    singleFilterQuery(e.entityId).delete
  }.map(_ => Done)

  private def updatePostalAddress(e: EventStreamElement[PostalAddressChanged]): DBIO[Done] = {
    singleFilterQuery(e.entityId)
      .map(r => (r.addressLine1,r.addressLine2,r.street,r.postalCode,r.city) )
      .update( ( Some(e.event.postalAddress.addressLine1), e.event.postalAddress.addressLine2, Some(e.event.postalAddress.streetLine), Some(e.event.postalAddress.postalCode), Some(e.event.postalAddress.city) ) )
  }.map(_ => Done)

  private def updateEmail(e:EventStreamElement[EmailChanged]): DBIO[Done] = {
    singleFilterQuery(e.entityId).map(_.email).update(e.event.email)
  }.map(_ => Done)

  private def updateStatus(e:EventStreamElement[StatusChanged]): DBIO[Done] = {
    singleFilterQuery(e.entityId).map(_.status).update(e.event.status)
  }.map(_ => Done)

  private def updatePersonalData(e: EventStreamElement[PersonalDataChanged]): DBIO[Done] = {
    singleFilterQuery(e.entityId).map(r => (r.salutation,r.firstName,r.lastName) ).update( (e.event.data.salutation,e.event.data.firstName,e.event.data.lastName) )
  }.map(_ => Done)

  private def insert(e: EventStreamElement[Created]): DBIO[Done] = {
    table += PatientData(
      e.event.number.trim,
      UUID.fromString(e.entityId),
      e.event.status,
      e.event.personalData.salutation,
      e.event.personalData.firstName,
      e.event.personalData.lastName,
      e.event.postalAddress.map(_.addressLine1),
      e.event.postalAddress.flatMap(_.addressLine2),
      e.event.postalAddress.map(_.streetLine),
      e.event.postalAddress.map(_.postalCode),
      e.event.postalAddress.map(_.city),
      e.event.email
    )
  }.map(_ => Done)

  //-------- Methods ----------------------

  implicit val statusScalar: JdbcType[PatientStatus] with BaseTypedType[PatientStatus] = MappedColumnType.base[PatientStatus,String](
    {b => b.name },
    {b => PatientStatus.withName(b) }
  )

  implicit val emailScalar: JdbcType[EmailAddress] with BaseTypedType[EmailAddress] = MappedColumnType.base[EmailAddress,String](
    {b => b.value},
    {b => EmailAddress(b) }
  )

  case class PatientData(number: String,
                         entityId: UUID,
                         status: PatientStatus,
                         salutation: String,
                         firstName: String,
                         lastName: String,
                         addressLine1: Option[String],
                         addressLine2: Option[String],
                         streetLine: Option[String],
                         postalCode: Option[String],
                         city: Option[String],
                         email: Option[EmailAddress])

  class PatientDataTable(tag: Tag) extends Table[PatientData](tag,tableName) {
    def number = column[String]("number", O.Unique)
    def entityId = column[UUID]("actor_entity_uuid", O.PrimaryKey)
    def status = column[PatientStatus]("status")
    def salutation = column[String]("salutation")
    def firstName = column[String]("first_name")
    def lastName = column[String]("surname")
    def addressLine1 = column[Option[String]]("address_line_one")
    def addressLine2 = column[Option[String]]("address_line_two")
    def street = column[Option[String]]("street_line")
    def postalCode = column[Option[String]]("postal_code")
    def city = column[Option[String]]("city")
    def email = column[Option[EmailAddress]]("email_address")

    def * = (number,entityId,status,salutation,firstName,lastName,addressLine1,addressLine2,street,postalCode,city,email) <> (PatientData.tupled, PatientData.unapply)
  }


  val table = TableQuery[PatientDataTable]


}

package de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates


import java.util.UUID

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

class BenefitRateProcessor(readSide: SlickReadSide)(implicit ec: ExecutionContext) extends ReadSideProcessor[BenefitRate.Event] {

  import BenefitRate._

  private val tableName = "benefit_rate_processor"

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[BenefitRate.Event] = readSide
    .builder[BenefitRate.Event](tableName+"_offset")
    .setGlobalPrepare( table.schema.createIfNotExists )
    .setEventHandler[BenefitRate.Created](insert)
    .setEventHandler[BenefitRate.Deactivated](d => changeStatus(false,UUID.fromString(d.entityId)))
    .setEventHandler[BenefitRate.Reactivated](d => changeStatus(true,UUID.fromString(d.entityId)))
    .setEventHandler[BenefitRate.Deleted](d => delete(UUID.fromString(d.entityId)))
    .build()

  override def aggregateTags: Set[AggregateEventTag[BenefitRate.Event]] = BenefitRate.Event.Tag.allTags

  //-------- Methods ----------------------

  private def delete(id: UUID): DBIO[Done] = {
    table.filter(_.actorId === id).delete
  }.map(_ => Done)

  private def changeStatus(status: Boolean, id: UUID): DBIO[Done] = {
    table.filter(_.actorId === id).map(_.active).update(status)
  }.map(_ => Done)

  private def insert(elem: EventStreamElement[BenefitRate.Created]):DBIO[Done] = {
    table += BenefitRepositoryData(
      elem.event.number.trim,
      elem.event.description, elem.event.isOfficialGebueH,
      elem.event.alternativeForGOAE,
      true,
      UUID.fromString(elem.entityId)
    )
  }.map(_ => Done)

  //-------- Methods ----------------------



  case class BenefitRepositoryData(number: String,
                                   description: String,
                                   isOfficialGebueH: Boolean,
                                   alternativeForGOAE: Option[String],
                                   active: Boolean,
                                   actorId: UUID)


  class BenefitRateTable(tag: Tag) extends Table[BenefitRepositoryData](tag,tableName) {

    def number = column[String]("number",O.Unique)
    def description = column[String]("description")
    def officialGebueH = column[Boolean]("gebueh")
    def alternativeGOAE = column[Option[String]]("alternative_goae")
    def active = column[Boolean]("active")
    def actorId = column[UUID]("actor_id",O.PrimaryKey)

    def * = (number,description,officialGebueH,alternativeGOAE,active,actorId) <> ((BenefitRepositoryData.apply _).tupled, BenefitRepositoryData.unapply )

  }

  val table = TableQuery[BenefitRateTable]


}

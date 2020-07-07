package de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates

import java.util.UUID

import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class BenefitRateRepository(database: Database)(implicit ec: ExecutionContext) {

  import BenefitRate._

  class BenefitRateTable(tag: Tag) extends Table[BenefitRepositoryData](tag,"BenefitRepository") {

    def number = column[String]("number",O.Unique)
    def description = column[String]("description")
    def officialGebueH = column[Boolean]("gebueh")
    def alternativeGOAE = column[Option[String]]("alternative_goae")
    def active = column[Boolean]("active")
    def actorId = column[UUID]("actor_id",O.PrimaryKey)

    def * = (number,description,officialGebueH,alternativeGOAE,active,actorId) <> ((BenefitRepositoryData.apply _).tupled, BenefitRepositoryData.unapply )

  }

  val table = TableQuery[BenefitRateTable]

  def createTable() = table.schema.createIfNotExists


  def getUUIDByNumber(number: String): Future[UUID] = database.run(table.filter(_.number.toLowerCase === number.toLowerCase).result.headOption).map(_.get.actorId)

  private def searchQuery(input: String) = table.filter(r => (r.number like s"%$input%") || (r.description like s"%$input%") || (r.alternativeGOAE like s"%$input%") ).distinctOn(_.actorId).result

  def search(input: String): Future[Seq[UUID]] = database.run(searchQuery(input.toLowerCase.trim)).map(_.map(_.actorId))

  def list(take: Int, drop: Int): Future[Seq[UUID]] = database.run( table.take(take).drop(drop).sortBy(_.number.asc).map(_.actorId).result )

  def searchByNumber(number: String): Future[Seq[UUID]] = database.run(table.filter(_.number.toLowerCase like s"%$number%" ).map(_.actorId).result)

  def findByNumber(number: String): Future[Option[UUID]] = database.run(table.filter(_.number.toLowerCase === number.toLowerCase).map(_.actorId).result.headOption)

  def length(active: Option[Boolean]): Future[Int] = {
    val query = active.map{e =>
      table.filter(d => d.active === e).length
    }.getOrElse( table.length )
    database.run( query.result )
  }

}


final case class BenefitRepositoryData(number: String,
                                       description: String,
                                       isOfficialGebueH: Boolean,
                                       alternativeForGOAE: Option[String],
                                       active: Boolean,
                                       actorId: UUID)
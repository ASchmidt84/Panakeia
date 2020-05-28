package de.heilpraktikerelbmarsch.security.impl.profiles

import java.util.UUID

import akka.Done
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class ProfileRepository(database: Database)(implicit ec: ExecutionContext) {
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._

  class ProfileTable(tag: Tag) extends Table[ProfileRepositoryData](tag,"profile"){

    def uuid = column[UUID]("uuid", O.Unique)
    def login = column[String]("login", O.PrimaryKey)
    def email = column[Option[String]]("email",O.Unique)

    def * = (uuid,login,email) <> ( (ProfileRepositoryData.apply _).tupled, ProfileRepositoryData.unapply )

  }

  val profileTable = TableQuery[ProfileTable]

  def createTable() = profileTable.schema.createIfNotExists

  private def queryFindByLogin(login: String) = profileTable.filter(r => r.login.toLowerCase === login.toLowerCase).result.headOption

  private def queryFindByUUID(uuid: UUID) = profileTable.filter(_.uuid === uuid).result.headOption

  private def queryFindByEmail(email: String) = profileTable.filter(_.email.toLowerCase === email.toLowerCase).result.headOption

  def findUUIDByLogin(login: String): Future[Option[UUID]] = database.run(queryFindByLogin(login)).map(r => r.map(_.id))

  def findUUIDByEmail(email: String): Future[Option[UUID]] = database.run(queryFindByEmail(email)).map(_.map(_.id))

  def findUUIDByEmailAndLogin(login: String, email: String): Future[Option[UUID]] = findUUIDByLogin(login).flatMap{
    case Some(i) => Future.successful(Some(i))
    case _ => findUUIDByEmail(email)
  }

  //Damit kann das nur jemand machen der Zugriff auf eine Databaseinstance hat
  def addProfile(uuid: UUID,
                 login: String,
                 email: Option[String]): DBIO[Done] = {
    profileTable
      .filter(r => r.email.toLowerCase === email.map(_.toLowerCase) || r.login.toLowerCase === login.toLowerCase || r.uuid === uuid)
      .result
      .headOption
      .flatMap{
        case None => profileTable += ProfileRepositoryData(uuid,login,email)
        case _ => DBIO.successful(Done)
      }
      .map(_ => Done)
      .transactionally
  }

  def removeProfile(uuid: UUID): DBIO[Done] = {
    profileTable.filter(_.uuid === uuid).delete.map{_ => Done}
  }

}


final case class ProfileRepositoryData(id: UUID, login: String, email: Option[String])
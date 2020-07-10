package de.heilpraktikerelbmarsch.patient.impl.patient

import java.util.UUID

import de.heilpraktikerelbmarsch.patient.api.adt.PatientStatus
import de.heilpraktikerelbmarsch.util.adt.contacts.EmailAddress
import org.joda.time.LocalDate
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class PatientRepository(database: slick.jdbc.JdbcBackend.Database,
                        processor: PatientProcessor)(implicit ec: ExecutionContext) {
  import slick.jdbc.PostgresProfile.api._
  import processor._

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

  def list(status: PatientStatus, take: Int, drop: Int): Future[Seq[UUID]] = database.run(
    processor.table.sortBy(_.number.asc).filter(_.status === status).take(take).drop(drop).map(_.entityId).result
  )

//  def searchBirthdate(date: LocalDate, take: Int, drop: Int): Future[Seq[UUID]] = {
//    processor.table.filter(_.)
//  }

  def searchByName(name: String): Future[Seq[UUID]] = {
    val search = "%"+name+"%"
    database.run(processor.table.filter(r => (r.firstName like search) || (r.lastName like search) ).sortBy(_.entityId).map(_.entityId).result)
  }

  def searchByEmail(mail: String): Future[Seq[UUID]] = {
    val search = "%"+mail.trim.toLowerCase+"%"
    database.run(
      processor
        .table
        .filter( d => d.email.nonEmpty && ( d.email.get.asColumnOf[String] like search ) )
        .sortBy(_.entityId)
        .map(_.entityId)
        .result
    )
  }

  def searchByStreet(input: String): Future[Seq[UUID]] = {
    val search = "%"+input+"%"
    database.run(
      processor.table.filter(_.street like search)
        .sortBy(_.entityId)
        .map(_.entityId)
        .result
    )
  }

  def searchByPostalCode(input: String): Future[Seq[UUID]] = {
    val search = "%"+input+"%"
    database.run(
      processor.table.filter(_.postalCode like search)
        .sortBy(_.entityId)
        .map(_.entityId)
        .result
    )
  }

  def searchByCity(input: String): Future[Seq[UUID]] = {
    val search = "%"+input+"%"
    database.run(
      processor.table.filter(_.city like search)
        .sortBy(_.entityId)
        .map(_.entityId)
        .result
    )
  }


}

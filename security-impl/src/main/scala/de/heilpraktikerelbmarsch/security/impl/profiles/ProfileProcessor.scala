package de.heilpraktikerelbmarsch.security.impl.profiles

import java.util.UUID

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, ReadSideProcessor}
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import de.heilpraktikerelbmarsch.security.impl.profiles.Profile._
import slick.dbio.DBIOAction

import scala.concurrent.ExecutionContext

class ProfileProcessor(readSide: SlickReadSide,
                       repository: ProfileRepository)(implicit ec: ExecutionContext) extends ReadSideProcessor[ProfileEvent] {

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[ProfileEvent] =
    readSide
    .builder[ProfileEvent]("profile-processor")
    .setGlobalPrepare(repository.createTable())
    .setEventHandler[ProfileCreated]{e =>
      repository.addProfile(UUID.fromString(e.entityId),e.event.login,e.event.emailAddress.map(_.value))
    }
    .setEventHandler[RoleAdded](_ => DBIOAction.successful(Done))
    .setEventHandler[RoleRemoved](_ => DBIOAction.successful(Done))
    .setEventHandler[PasswordChanged](_ => DBIOAction.successful(Done))
    .setEventHandler[Deleted]{e =>
      repository.removeProfile(UUID.fromString(e.entityId))
    }
    .build()

  override def aggregateTags: Set[AggregateEventTag[ProfileEvent]] = ProfileEvent.Tag.allTags

}

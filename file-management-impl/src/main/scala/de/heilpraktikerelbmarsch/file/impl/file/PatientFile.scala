package de.heilpraktikerelbmarsch.file.impl.file

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import de.heilpraktikerelbmarsch.file.api.adt.{FileStatus, PatientView}
import de.heilpraktikerelbmarsch.file.impl.file.adt.Entry
import de.heilpraktikerelbmarsch.util.adt.contacts.Operator
import org.joda.time.DateTime
import play.api.libs.json.{Json, OFormat}

import scala.language.implicitConversions

object PatientFile {
  import julienrf.json.derived
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._
  import PatientView._


  // Commands ------------------------------------------------------------------------------------------------------>

  // This is a marker trait for shopping cart commands.
  // We will serialize them using Akka's Jackson support that is able to deal with the replyTo field.
  // (see application.conf).
  // Keep in mind that when configuring it on application.conf you need to use the FQCN which is:
  // com.example.shoppingcart.impl.ShoppingCart$CommandSerializable
  // Note the "$".
  trait CommandSerializable

  sealed trait Command extends CommandSerializable {
    val replyTo: ActorRef[Confirmation]
  }


  // Commands <------------------------------------------------------------------------------------------------------
  // Replies ------------------------------------------------------------------------------------------------------->

  final case class Summary()

  sealed trait Confirmation
  final case class CommandAccepted(summary: Summary) extends Confirmation
  final case class CommandRejected(error: String) extends Confirmation

  implicit val summaryJson: OFormat[Summary] = Json.format
  implicit val cmdAcceptedJson: OFormat[CommandAccepted] = Json.format
  implicit val cmdRejectedJson: OFormat[CommandRejected] = Json.format
  implicit val confirmationJson: OFormat[Confirmation] = derived.oformat()

  // Replies <-------------------------------------------------------------------------------------------------------

  // Events -------------------------------------------------------------------------------------------------------->

  sealed trait Event extends AggregateEvent[Event]{
    override def aggregateTag: AggregateEventTagger[Event] = Event.Tag
    val operator: Operator
    val timestamp: DateTime
  }

  object Event {
    val Tag: AggregateEventShards[Event] = AggregateEventTag.sharded[Event](2)
  }

  // Events <--------------------------------------------------------------------------------------------------------

  val empty: PatientFile = PatientFile(
    None, DateTime.now(), FileStatus.Init,
    Nil
  )

  val typedKey: EntityTypeKey[Command] = EntityTypeKey[Command]("PatientFile")

  val serializer: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer(summaryJson),
    JsonSerializer(cmdAcceptedJson),
    JsonSerializer(cmdRejectedJson),
    JsonSerializer(confirmationJson)
  )

  def apply(persistenceId: PersistenceId): EventSourcedBehavior[Command, Event, PatientFile] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, PatientFile](
        persistenceId = persistenceId,
        emptyState = empty,
        commandHandler = (p, cmd) => p.applyCommand(cmd),
        eventHandler = (p, evt) => p.applyEvent(evt)
      )
  }


  def apply(entityContext: EntityContext[Command]): Behavior[Command] =
    apply(PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 500, keepNSnapshots = 2))

}


final case class PatientFile(patient: Option[PatientView],
                             createDate: DateTime,
                             status: FileStatus,
                             entries: Seq[Entry]) {
  import PatientFile._


  def applyCommand(cmd: Command): ReplyEffect[Event, PatientFile] = ???

  def applyEvent(evt: Event): PatientFile = ???

}
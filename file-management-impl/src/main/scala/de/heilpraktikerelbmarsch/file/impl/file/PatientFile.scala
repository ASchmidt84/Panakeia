package de.heilpraktikerelbmarsch.file.impl.file

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import de.heilpraktikerelbmarsch.file.api.adt.{Entry, FileStatus, PatientView}
import de.heilpraktikerelbmarsch.util.adt.contacts.Operator
import org.joda.time.DateTime
import play.api.libs.json.{Json, OFormat}

import scala.language.implicitConversions

object PatientFile {
  import julienrf.json.derived
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._
  import PatientView._
  import FileStatus._
  import Operator._
  import Entry._


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

  final case class Get(replyTo: ActorRef[Confirmation]) extends Command

  final case class Create(patientView: PatientView,
                          operator: Operator,
                          replyTo: ActorRef[Confirmation]) extends Command

  final case class Close(operator: Operator,
                         reason: String,
                         replyTo: ActorRef[Confirmation]) extends Command

  final case class Reopen(operator: Operator,
                          reason: String,
                          replyTo: ActorRef[Confirmation]) extends Command

  final case class AddEntry(entry: Entry,
                            operator: Operator,
                            replyTo: ActorRef[Confirmation]) extends Command


  // Commands <------------------------------------------------------------------------------------------------------
  // Replies ------------------------------------------------------------------------------------------------------->

  final case class Summary(patient: PatientView,
                           createDate: DateTime,
                           entries: Seq[Entry],
                           status: FileStatus)

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

  final case class Created(patientView: PatientView,
                           operator: Operator,
                           timestamp: DateTime = DateTime.now()) extends Event

  final case class Closed(reason: String,
                          operator: Operator,
                          timestamp: DateTime = DateTime.now()) extends Event

  final case class Reopened(reason: String,
                            operator: Operator,
                            timestamp: DateTime = DateTime.now()) extends Event

  final case class EntryAdded(entry: Entry,
                              operator: Operator,
                              timestamp: DateTime = DateTime.now()) extends Event

  implicit val createdJsonFormat: OFormat[Created] = Json.format
  implicit val closedJsonFormat: OFormat[Closed] = Json.format
  implicit val reopenedJsonFormat: OFormat[Reopened] = Json.format
  implicit val entryAddedJsonFormat: OFormat[EntryAdded] = Json.format

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
    JsonSerializer(confirmationJson),
    JsonSerializer(createdJsonFormat),
    JsonSerializer(closedJsonFormat),
    JsonSerializer(reopenedJsonFormat),
    JsonSerializer(entryAddedJsonFormat)
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

  implicit private def toSummary(x: PatientFile): Summary = Summary(
    x.patient.get, x.createDate, x.entries, x.status
  )


  def applyCommand(cmd: Command): ReplyEffect[Event, PatientFile] = cmd match {
    case Get(reply) if status != FileStatus.Init =>
      Effect.reply(reply)(CommandAccepted(this))
    case s if status == FileStatus.Closed => s match {
      case Reopen(op,reason,reply) =>
        Effect.persist( Reopened(reason,op) ).thenReply(reply)(r => CommandAccepted(r))
      case f: Command => Effect.reply(f.replyTo)(CommandRejected("file closed, please reopen first"))
    }
    case s if status == FileStatus.Init => s match {
      case Create(p,op,reply) =>
        Effect.persist( Created(p,op) ).thenReply(reply)(r => CommandAccepted(r))
      case f: Command => Effect.reply(f.replyTo)(CommandRejected("file is not initialized, please initialize first"))
    }
    case AddEntry(entry,_,reply) if entries.exists(_.timestamp.isAfter(entry.timestamp) ) =>
      Effect.reply(reply)(CommandRejected("impossible to add an entry in the past"))
    case AddEntry(entry,op,reply) =>
      Effect.persist( EntryAdded(entry,op) ).thenReply(reply)(r => CommandAccepted(r))
    case Close(op,reason,reply) =>
      Effect.persist(Closed(reason,op)).thenReply(reply)(r => CommandAccepted(r) )
    case f: Command => Effect.reply(f.replyTo)(CommandRejected("this command is invalid"))
  }

  def applyEvent(evt: Event): PatientFile = evt match {
    case Created(view,_,time) =>
      copy(patient = Some(view), createDate = time, status = FileStatus.Open)
    case Closed(_,_,_) =>
      copy(status = FileStatus.Closed)
    case Reopened(_,_,_) =>
      copy(status = FileStatus.Open)
    case EntryAdded(entry,_,_) =>
      copy( entries = (entry :: this.entries.toList).sortBy(_.timestamp.getMillis) )
  }

}


object SerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = PatientFile.serializer
}
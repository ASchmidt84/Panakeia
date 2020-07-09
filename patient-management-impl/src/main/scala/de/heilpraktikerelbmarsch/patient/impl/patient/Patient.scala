package de.heilpraktikerelbmarsch.patient.impl.patient

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import de.heilpraktikerelbmarsch.util.adt.contacts.Operator
import org.joda.time.DateTime
import play.api.libs.json.{Format, JsResult, JsValue, Json, OFormat}

import scala.language.implicitConversions

object Patient {
  import julienrf.json.derived
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._


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

  final case class Summary(number: String,
                           description: String,
                           isOfficialGebueH: Boolean,
                           alternativeForGOAE: Option[String],
                           active: Boolean,
                           deleted: Boolean)

  sealed trait Confirmation
  final case class CommandAccepted(summary: Summary) extends Confirmation
  final case class CommandRejected(error: String) extends Confirmation

  implicit val summaryJson: OFormat[Summary] = Json.format
  implicit val cmdAcceptedJson: OFormat[CommandAccepted] = Json.format
  implicit val cmdRejectedJson: OFormat[CommandRejected] = Json.format
  implicit val confirmationJson: OFormat[Confirmation] = derived.oformat() /*new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if( (json \ "reason").isDefined ){
        Json.fromJson[CommandRejected](json)
      } else Json.fromJson[CommandAccepted](json)
    }

    override def writes(o: Confirmation): JsValue = o match {
      case f: CommandRejected => Json.toJson(f)
      case f: CommandAccepted => Json.toJson(f)
    }
  }*/

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

  val empty: Patient = ???

  val typedKey: EntityTypeKey[Command] = EntityTypeKey[Command]("BenefitRate")

  val serializer: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer(summaryJson),
    JsonSerializer(cmdAcceptedJson),
    JsonSerializer(cmdRejectedJson),
    JsonSerializer(confirmationJson)
  )

  def apply(persistenceId: PersistenceId): EventSourcedBehavior[Command, Event, Patient] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, Patient](
        persistenceId = persistenceId,
        emptyState = empty,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
  }


  def apply(entityContext: EntityContext[Command]): Behavior[Command] =
    apply(PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 500, keepNSnapshots = 2))

}

final case class Patient() {
  import Patient._

  def applyCommand(cmd: Command): ReplyEffect[Event, Patient] = ???

  def applyEvent(evt: Event): Patient = ???

}



object PatientSerializer extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Patient.serializer
}
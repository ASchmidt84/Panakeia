package de.heilpraktikerelbmarsch.benefitRate.impl.benefitRates

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import de.heilpraktikerelbmarsch.benefitRate.api.adt.Rate
import de.heilpraktikerelbmarsch.util.adt.contacts.Operator
import org.joda.time.DateTime
import play.api.libs.json.{Format, JsResult, JsValue, Json, OFormat}

import scala.language.implicitConversions


object BenefitRate {
  import Rate._
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._

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

  final case class CreateBenefitRate(number: String,
                                     description: String,
                                     isOfficialGebueH: Boolean,
                                     alternativeForGOAE: Option[String],
                                     replyTo: ActorRef[Confirmation],
                                     operator: Operator) extends Command

  final case class Get(replyTo: ActorRef[Confirmation]) extends Command

  final case class SetRate(rate: Rate,
                           replyTo: ActorRef[Confirmation],
                           operator: Operator) extends Command

  final case class Deactivate(replyTo: ActorRef[Confirmation],
                              operator: Operator) extends Command

  final case class Reactivate(replyTo: ActorRef[Confirmation],
                              operator: Operator) extends Command

  final case class RemoveRate(rate: Rate,
                              replyTo: ActorRef[Confirmation],
                              operator: Operator) extends Command

  final case class Delete(reason: String,
                          replyTo: ActorRef[Confirmation],
                          operator: Operator) extends Command

  final case class Restore(reason: String,
                           operator: Operator,
                           replyTo: ActorRef[Confirmation]) extends Command

  // Confirmations! also Replies! --------------------------------------------------------------------------------

  final case class Summary(number: String,
                           description: String,
                           isOfficialGebueH: Boolean,
                           alternativeForGOAE: Option[String],
                           rates: Seq[Rate],
                           active: Boolean,
                           deleted: Boolean)

  sealed trait Confirmation
  final case class CommandAccepted(summary: Summary) extends Confirmation
  final case class CommandRejected(error: String) extends Confirmation

  implicit val summaryJson: Format[Summary] = Json.format
  implicit val cmdAcceptedJson: Format[CommandAccepted] = Json.format
  implicit val cmdRejectedJson: Format[CommandRejected] = Json.format
  implicit val confirmationJson: Format[Confirmation] = new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if( (json \ "reason").isDefined ){
        Json.fromJson[CommandRejected](json)
      } else Json.fromJson[CommandAccepted](json)
    }

    override def writes(o: Confirmation): JsValue = o match {
      case f: CommandRejected => Json.toJson(f)
      case f: CommandAccepted => Json.toJson(f)
    }
  }

  // Events! -----------------------------------------------------------------------------------------------------

  sealed trait Event extends AggregateEvent[Event]{
    override def aggregateTag: AggregateEventTagger[Event] = Event.Tag
    val operator: Operator
    val timestamp: DateTime
  }

  object Event {
    val Tag: AggregateEventShards[Event] = AggregateEventTag.sharded[Event](2)
  }

  final case class Created(number: String,
                           description: String,
                           isOfficialGebueH: Boolean,
                           alternativeForGOAE: Option[String],
                           operator: Operator,
                           timestamp: DateTime = DateTime.now()) extends Event

  implicit val createdJson: OFormat[Created] = Json.format

  final case class RateSet(rate: Rate,
                           operator: Operator,
                           timestamp: DateTime = DateTime.now()) extends Event

  implicit val rateSetJson: OFormat[RateSet] = Json.format

  final case class Deactivated(operator: Operator,
                               timestamp: DateTime = DateTime.now()) extends Event

  implicit val deactivatedJson: OFormat[Deactivated] = Json.format

  final case class Reactivated(operator: Operator,
                               timestamp: DateTime = DateTime.now()) extends Event

  implicit val reactivatedJson: OFormat[Reactivated] = Json.format

  final case class RateRemoved(rate: Rate,
                               operator: Operator,
                               timestamp: DateTime = DateTime.now()) extends Event

  implicit val rateRemovedJson: OFormat[RateRemoved] = Json.format

  final case class Deleted(reason: String,
                           operator: Operator,
                           timestamp: DateTime = DateTime.now()) extends Event

  final case class Restored(reason: String,
                            operator: Operator,
                            timestamp: DateTime = DateTime.now()) extends Event


  implicit val formatDeleted: OFormat[Deleted] = Json.format
  implicit val formatRestored: OFormat[Restored] = Json.format

  // Events! ENDE ------------------------------------------------------------------------------------------------

  val empty: BenefitRate = BenefitRate("","",false,None,Nil)

  val typedKey: EntityTypeKey[Command] = EntityTypeKey[Command]("BenefitRate")

  def apply(persistenceId: PersistenceId): EventSourcedBehavior[Command, Event, BenefitRate] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, BenefitRate](
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

  implicit val benefitRateJson: OFormat[BenefitRate] = Json.format

}

/**
 * Leistungsatz
 * The benefit rate is the payment rate for a given service or benefit.
 * This BenefitRate describes a service and can contains multiple rates which determine which type
 * this payment is for. Maybe 14 € for private payer and 12 € for an with Beihilfe
 * @param number the number of the benefit rate
 * @param description the description of the benefit
 * @param isOfficialGebueH is this a official GebüH BenefitRate
 * @param alternativeForGOAE Some means that this is an alternative for an GOÄ Number
 * @param rates The rates (payments) for this benefit
 * @param active is an active one or not
 */
final case class BenefitRate(number: String,
                             description: String,
                             isOfficialGebueH: Boolean,
                             alternativeForGOAE: Option[String],
                             rates: Seq[Rate],
                             active: Boolean = true,
                             deleted: Boolean = false) {
  import BenefitRate._

  def isNotInitialized: Boolean = this == empty

  implicit private def toSummary(x: BenefitRate): Summary = Summary(
    x.number,
    x.description,
    x.isOfficialGebueH,
    x.alternativeForGOAE,
    x.rates.distinctBy(_.settlementType),
    x.active, x.deleted
  )

  private def reject(reply: ActorRef[Confirmation])(msg: String): ReplyEffect[Event,BenefitRate] = Effect.reply(reply)(CommandRejected(msg))

  def applyCommand(command: Command): ReplyEffect[Event,BenefitRate] = command match {
    case x: Command if isNotInitialized && !deleted => x match {
      case CreateBenefitRate(number,desc,gebu,alternative,reply,operator) if isNotInitialized =>
        onCreate(number,desc,gebu,alternative,operator,reply)
      case _ => reject(x.replyTo)(s"Impossible! The entity is not initialized")
    }
    case j if !deleted =>  j match {
      case x: CreateBenefitRate => reject(x.replyTo)(s"I cannot create this on an existing entity with number ${this.number}")
      case Deactivate(reply,op) => onDeactivate(op,reply)
      case Reactivate(reply,op) => onActivate(op,reply)
      case SetRate(rate,reply,operator) => onSetRate(rate,operator,reply)
      case Get(reply) => Effect.reply(reply)(CommandAccepted(this))
      case RemoveRate(rate,reply,op) => onRemoveRate(rate,op,reply)
      case Delete(reason,reply,op) => onDelete(reason,op,reply)
      case j => reject(j.replyTo)("This entity was deleted or is not initialized")
    }
    case Restore(reason,op,reply) if !isNotInitialized => onRestore(reason,op,reply)
    case j => reject(j.replyTo)("This entity was deleted or is not initialized")
  }

  private def onRestore(reason: String,
                        operator: Operator,
                        reply: ActorRef[Confirmation]): ReplyEffect[Event,BenefitRate] = {
    Effect
      .persist(Restored(reason,operator))
      .thenReply(reply)(d => CommandAccepted(d))
  }

  private def onDelete(reason: String,
                       operator: Operator,
                       reply: ActorRef[Confirmation]): ReplyEffect[Event,BenefitRate] = {
    Effect
      .persist( Deactivated(operator), Deleted(reason,operator) )
      .thenReply(reply)(s => CommandAccepted(s) )
  }

  private def onRemoveRate(rate: Rate,
                           operator: Operator,
                           reply: ActorRef[Confirmation]): ReplyEffect[Event,BenefitRate] = {
    val found = rates.find(_.settlementType == rate.settlementType)
    if(found.isDefined){
      Effect.persist( RateRemoved(found.get,operator) ).thenReply(reply)(s => CommandAccepted(s))
    } else reject(reply)(s"Impossible! The $rate was not found!")
  }

  private def onSetRate(rate: Rate,
                        operator: Operator,
                        reply: ActorRef[Confirmation]): ReplyEffect[Event,BenefitRate] = {
    Effect.persist(RateSet(rate,operator)).thenReply(reply)(r => CommandAccepted(r))
  }

  private def onActivate(operator: Operator,
                         reply: ActorRef[Confirmation]): ReplyEffect[Event,BenefitRate] = {
    if(active) reject(reply)(s"Cannot comply, $number is already activated")
    else
      Effect.persist(Reactivated(operator)).thenReply(reply)(s => CommandAccepted(s))
  }

  private def onDeactivate(operator: Operator,
                           reply: ActorRef[Confirmation]): ReplyEffect[Event,BenefitRate] = {
    if(active) {
      Effect.persist(Deactivated(operator)).thenReply(reply)(s => CommandAccepted(s))
    } else reject(reply)(s"Cannot comply, $number is already deactivated")
  }

  private def onCreate(number: String,
                       description: String,
                       isOfficialGebueH: Boolean,
                       alternativeForGOAE: Option[String],
                       operator: Operator,
                       replyTo: ActorRef[Confirmation]): ReplyEffect[Event,BenefitRate] = {
    Effect
        .persist(
          Created(number,description,isOfficialGebueH,alternativeForGOAE,operator)
        )
        .thenReply(replyTo)( state => CommandAccepted(toSummary(state)) )
  }

  def applyEvent(evt: Event): BenefitRate = evt match {
    case Created(number,desc,official,goa,_,_) => onCreated(number,desc,official,goa)
    case Reactivated(_,_) => onActiveChange(true)
    case Deactivated(_,_) => onActiveChange(false)
    case RateSet(rate,_,_) => onRateSet(rate)
    case RateRemoved(rate,_,_) => onRateRemoved(rate)
    case Deleted(_,_,_) => copy(deleted = true)
    case Restored(_,_,_) => copy(deleted = false)
  }

  private def onRateSet(rate: Rate): BenefitRate = {
    val tmp = rate :: this.rates.filterNot(_.settlementType == rate.settlementType).toList
    this.copy(rates = tmp.distinctBy(_.settlementType) )
  }

  private def onRateRemoved(rate: Rate) = this.copy(rates = this.rates.filterNot(_ == rate).distinctBy(_.settlementType) )

  private def onActiveChange(x: Boolean) = this.copy(active = x)

  private def onCreated(number: String,
                        description: String,
                        isOfficialGebueH: Boolean,
                        alternativeForGOAE: Option[String]) = this.copy(
    number = number, description = description, isOfficialGebueH = isOfficialGebueH, alternativeForGOAE = alternativeForGOAE
  )

}


object SerializerRegistry extends JsonSerializerRegistry {
  import BenefitRate._

  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[BenefitRate],
    JsonSerializer[Summary],
    JsonSerializer[CommandAccepted],
    JsonSerializer[CommandRejected],
    JsonSerializer[Confirmation],
    //
    JsonSerializer[Created],
    JsonSerializer[Reactivated],
    JsonSerializer[Deactivated],
    JsonSerializer[RateSet],
    JsonSerializer[RateRemoved],
    JsonSerializer[Deleted],
    JsonSerializer[Restored]
  )
}
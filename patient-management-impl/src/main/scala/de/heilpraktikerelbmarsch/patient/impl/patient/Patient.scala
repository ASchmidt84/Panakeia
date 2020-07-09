package de.heilpraktikerelbmarsch.patient.impl.patient

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import de.heilpraktikerelbmarsch.patient.api.adt.PatientStatus
import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, Operator, PersonalData, PhoneNumber, PostalAddress}
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.{Json, OFormat}

import scala.language.implicitConversions

object Patient {
  import julienrf.json.derived
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._
  import PersonalData._
  import PostalAddress._
  import PhoneNumber._
  import EmailAddress._
  import PatientStatus._


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

  final case class Create(number: String,
                          status: PatientStatus,
                          personalData: PersonalData,
                          postalAddress: Option[PostalAddress],
                          birthdate: Option[LocalDate],
                          email: Option[EmailAddress],
                          phonePrivate: Option[PhoneNumber],
                          phoneWork: Option[PhoneNumber],
                          cellPhone: Option[PhoneNumber],
                          fax: Option[PhoneNumber],
                          job: Option[String],
                          operator: Operator,
                          replyTo: ActorRef[Confirmation]) extends Command

  final case class ChangeStatus(status: PatientStatus,
                                operator: Operator,
                                replyTo: ActorRef[Confirmation]) extends Command

  final case class ChangePersonalData(data: PersonalData,
                                      operator: Operator,
                                      replyTo: ActorRef[Confirmation]) extends Command

  final case class ChangePostalAddress(postalAddress: PostalAddress,
                                       operator: Operator,
                                       replyTo: ActorRef[Confirmation]) extends Command

  final case class ChangePhoneData(phonePrivate: Option[PhoneNumber],
                                   phoneWork: Option[PhoneNumber],
                                   cellPhone: Option[PhoneNumber],
                                   fax: Option[PhoneNumber],
                                   operator: Operator,
                                   replyTo: ActorRef[Confirmation]) extends Command

  final case class ChangeEmail(email: Option[EmailAddress],
                               operator: Operator,
                               replyTo: ActorRef[Confirmation]) extends Command

  final case class ChangeJob(job: Option[String],
                             operator: Operator,
                             replyTo: ActorRef[Confirmation]) extends Command


  // Commands <------------------------------------------------------------------------------------------------------
  // Replies ------------------------------------------------------------------------------------------------------->

  final case class Summary(number: String,
                           status: PatientStatus,
                           personalData: PersonalData,
                           postalAddress: Option[PostalAddress],
                           birthdate: Option[LocalDate],
                           email: Option[EmailAddress],
                           phonePrivate: Option[PhoneNumber],
                           phoneWork: Option[PhoneNumber],
                           cellPhone: Option[PhoneNumber],
                           fax: Option[PhoneNumber],
                           job: Option[String])

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

  final case class Created(number: String,
                           status: PatientStatus,
                           personalData: PersonalData,
                           postalAddress: Option[PostalAddress],
                           birthdate: Option[LocalDate],
                           email: Option[EmailAddress],
                           phonePrivate: Option[PhoneNumber],
                           phoneWork: Option[PhoneNumber],
                           cellPhone: Option[PhoneNumber],
                           fax: Option[PhoneNumber],
                           job: Option[String],
                           operator: Operator,
                           timestamp: DateTime = DateTime.now() ) extends Event

  final case class StatusChanged(status: PatientStatus,
                                 operator: Operator,
                                 timestamp: DateTime = DateTime.now()) extends Event

  final case class PersonalDataChanged(data: PersonalData,
                                       operator: Operator,
                                       timestamp: DateTime = DateTime.now()) extends Event

  final case class PostalAddressChanged(postalAddress: PostalAddress,
                                        operator: Operator,
                                        timestamp: DateTime = DateTime.now()) extends Event

  final case class PhoneDataChanged(phonePrivate: Option[PhoneNumber],
                                    phoneWork: Option[PhoneNumber],
                                    cellPhone: Option[PhoneNumber],
                                    fax: Option[PhoneNumber],
                                    operator: Operator,
                                    timestamp: DateTime = DateTime.now()) extends Event

  final case class EmailChanged(email: Option[EmailAddress],
                                operator: Operator,
                                timestamp: DateTime = DateTime.now()) extends Event

  final case class JobChanged(job: Option[String],
                              operator: Operator,
                              timestamp: DateTime = DateTime.now()) extends Event

  implicit val jsonFormatCreated: OFormat[Created] = Json.format
  implicit val jsonFormatStatusChanged: OFormat[StatusChanged] = Json.format
  implicit val jsonFormatPersonalDataChanged: OFormat[PersonalDataChanged] = Json.format
  implicit val jsonFormatPostalAddressChanged: OFormat[PostalAddressChanged] = Json.format
  implicit val jsonFormatPhoneDataChanged: OFormat[PhoneDataChanged] = Json.format
  implicit val jsonFormatEmailChanged: OFormat[EmailChanged] = Json.format
  implicit val jsonFormatJobChanged: OFormat[JobChanged] = Json.format

  // Events <--------------------------------------------------------------------------------------------------------

  val empty: Patient = Patient(
    "??????????????????",
    PatientStatus.InActive,
    PersonalData("???????????","?????????????","?????????????")
  )

  val typedKey: EntityTypeKey[Command] = EntityTypeKey[Command]("BenefitRate")

  val serializer: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer(summaryJson),
    JsonSerializer(cmdAcceptedJson),
    JsonSerializer(cmdRejectedJson),
    JsonSerializer(confirmationJson),

    JsonSerializer(jsonFormatCreated),
    JsonSerializer(jsonFormatStatusChanged),
    JsonSerializer(jsonFormatPersonalDataChanged),
    JsonSerializer(jsonFormatPostalAddressChanged),
    JsonSerializer(jsonFormatPhoneDataChanged),
    JsonSerializer(jsonFormatEmailChanged),
    JsonSerializer(jsonFormatJobChanged),
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

final case class Patient(number: String,
                         status: PatientStatus,
                         personalData: PersonalData,
                         postalAddress: Option[PostalAddress] = None,
                         birthdate: Option[LocalDate] = None,
                         email: Option[EmailAddress] = None,
                         phonePrivate: Option[PhoneNumber] = None,
                         phoneWork: Option[PhoneNumber] = None,
                         cellPhone: Option[PhoneNumber] = None,
                         fax: Option[PhoneNumber] = None,
                         job: Option[String] = None) {
  import Patient._

  private def isInit = number == empty.number || personalData == empty.personalData

  implicit def toSummary(x: Patient): Summary = Summary(
    x.number,x.status,x.personalData,x.postalAddress,
    x.birthdate,x.email,x.phonePrivate,x.phoneWork,
    x.cellPhone,x.fax,x.job
  )

  def applyCommand(cmd: Command): ReplyEffect[Event, Patient] = cmd match {
    case c: Create if isInit => onCreate(c)
    case x: Command => Effect.reply(x.replyTo)(CommandRejected("Unknown command"))
  }

  private def onCreate(cmd: Create): ReplyEffect[Event, Patient] = {
    Effect
      .persist(Created(
        cmd.number, cmd.status, cmd.personalData, cmd.postalAddress,
        cmd.birthdate, cmd.email, cmd.phonePrivate, cmd.phoneWork,
        cmd.cellPhone,cmd.fax,cmd.job,cmd.operator
      ))
      .thenReply(cmd.replyTo)( r => CommandAccepted(toSummary(r)) )
  }

  def applyEvent(evt: Event): Patient = evt match {
    case x: Created => onCreated(x)
    case _ => this
  }


  private def onCreated(x: Created): Patient = copy(
    number = x.number,
    status = x.status,
    personalData = x.personalData,
    postalAddress = x.postalAddress,
    birthdate = x.birthdate,
    email = x.email,
    phonePrivate = x.phonePrivate,
    phoneWork = x.phoneWork,
    cellPhone = x.cellPhone,
    fax = x.fax,
    job = x.job
  )

}



object SerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Patient.serializer
}
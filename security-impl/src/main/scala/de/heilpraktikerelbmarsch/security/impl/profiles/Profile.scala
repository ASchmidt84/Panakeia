package de.heilpraktikerelbmarsch.security.impl.profiles

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl._
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger, AkkaTaggerAdapter, PersistentEntity}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import de.heilpraktikerelbmarsch.util.adt.contacts.{EmailAddress, PersonalData}
import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles
import org.joda.time.DateTime
import org.pac4j.core.credentials.password.JBCryptPasswordEncoder
import play.api.libs.json.{Format, JsResult, JsValue, Json, OFormat}

object Profile {
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._
  import EmailAddress._
  import DefaultRoles._
  import PersonalData._


  // This is a marker trait for shopping cart commands.
  // We will serialize them using Akka's Jackson support that is able to deal with the replyTo field.
  // (see application.conf).
  // Keep in mind that when configuring it on application.conf you need to use the FQCN which is:
  // com.example.shoppingcart.impl.ShoppingCart$CommandSerializable
  // Note the "$".
  trait ProfileCommandSerializable

  sealed trait Command extends ProfileCommandSerializable {
    val replyTo: ActorRef[ProfileConfirmation]
  }

  final case class CreateProfile(login: String,
                                 password: String,
                                 email: Option[EmailAddress],
                                 roles: Set[DefaultRoles],
                                 name: PersonalData,
                                 replyTo: ActorRef[ProfileConfirmation]) extends Command

  final case class AddRole(role: DefaultRoles,replyTo: ActorRef[ProfileConfirmation]) extends Command
  final case class RemoveRole(role: DefaultRoles,replyTo: ActorRef[ProfileConfirmation]) extends Command
  final case class ChangePassword(password: String,replyTo: ActorRef[ProfileConfirmation]) extends Command
  final case class Get(replyTo: ActorRef[ProfileConfirmation]) extends Command
  final case class DeleteProfile(replyTo: ActorRef[ProfileConfirmation]) extends Command
  final case class ValidatePassword(plainPassword: String, replyTo: ActorRef[ProfileConfirmation]) extends Command


  // Confirmations! also Replies! --------------------------------------------------------------------------------
  final case class ProfileSummary(login: String,
                                  email: Option[EmailAddress],
                                  roles: Set[DefaultRoles],
                                  name: PersonalData)

  sealed trait ProfileConfirmation

  final case class ProfileCmdAccepted(summary: ProfileSummary) extends ProfileConfirmation

  final case class ProfileCmdRejected(reason: String) extends ProfileConfirmation

  implicit val profileSummaryJson: Format[ProfileSummary] = Json.format
  implicit val profileCmdAcceptedJson: Format[ProfileCmdAccepted] = Json.format
  implicit val profileCmdRejectedJson: Format[ProfileCmdRejected] = Json.format
  implicit val confirmationJson: Format[ProfileConfirmation] = new Format[ProfileConfirmation] {
    override def reads(json: JsValue): JsResult[ProfileConfirmation] = {
      if( (json \ "reason").isDefined ){
        Json.fromJson[ProfileCmdRejected](json)
      } else Json.fromJson[ProfileCmdAccepted](json)
    }

    override def writes(o: ProfileConfirmation): JsValue = o match {
      case f: ProfileCmdRejected => Json.toJson(f)
      case f: ProfileCmdAccepted => Json.toJson(f)
    }
  }

  // Events! -----------------------------------------------------------------------------------------------------

  sealed trait ProfileEvent extends AggregateEvent[ProfileEvent]{
    override def aggregateTag: AggregateEventTagger[ProfileEvent] = ProfileEvent.Tag
  }

  object ProfileEvent {
    val Tag: AggregateEventShards[ProfileEvent] = AggregateEventTag.sharded[ProfileEvent](2)
  }

  final case class ProfileCreated(login: String,
                                  passwordHash: String,
                                  salt: Option[String],
                                  roles: Seq[DefaultRoles],
                                  emailAddress: Option[EmailAddress],
                                  name: PersonalData,
                                  dateTime: DateTime) extends ProfileEvent

  implicit val profileCreatedJson: Format[ProfileCreated] = Json.format

  final case class RoleAdded(role: DefaultRoles,
                             dateTime: DateTime) extends ProfileEvent

  implicit val roleAddedJson: Format[RoleAdded] = Json.format

  final case class RoleRemoved(role: DefaultRoles, dateTime: DateTime) extends ProfileEvent

  implicit val roleRemovedJson: Format[RoleRemoved] = Json.format

  final case class PasswordChanged(password: String,
                                   salt: Option[String],
                                   dateTime: DateTime) extends ProfileEvent

  implicit val passwordChangedJson: Format[PasswordChanged] = Json.format

  final case class Deleted(dateTime: DateTime) extends ProfileEvent

  implicit val deletedJson: Format[Deleted] = Json.format

  // Events! ENDE ------------------------------------------------------------------------------------------------

  val empty: Profile = Profile("","",None,Nil,None,PersonalData("","",""))

  val typedKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ProfileEntity")

  def apply(persistenceId: PersistenceId): EventSourcedBehavior[Command, ProfileEvent, Profile] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, ProfileEvent, Profile](
        persistenceId = persistenceId,
        emptyState = Profile.empty,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
  }


  def apply(entityContext: EntityContext[Command]): Behavior[Command] =
    apply(PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, ProfileEvent.Tag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))



  implicit val profileJson: Format[Profile] = Json.format

}

final case class Profile(login: String,
                         passwordHash: String,
                         salt: Option[String],
                         roles: Seq[DefaultRoles],
                         emailAddress: Option[EmailAddress],
                         name: PersonalData,
                         deleted: Boolean = false) {

  import Profile._

  def isNotInitialized: Boolean = this == empty

  private def toSummary(x: Profile) = ProfileSummary(x.login,x.emailAddress,x.roles.toSet,x.name)

  def applyCommand(cmd: Command): ReplyEffect[ProfileEvent,Profile] = {
    if(isNotInitialized){
      cmd match {
        case CreateProfile(login, password, email, roles, name, replyTo) =>
          onCreate(login,password,email,roles,name,replyTo)
        case a => Effect.reply(a.replyTo)(ProfileCmdRejected("The profile must be initialized before using it"))
      }
    } else {
      cmd match {
        case a if deleted => Effect.reply(a.replyTo)(ProfileCmdRejected("The profile was deleted, unable to accept any command"))
        case AddRole(role,replyTo) => onAddRole(role,replyTo)
        case RemoveRole(role,replyTo) => onRemoveRole(role,replyTo)
        case ChangePassword(password,replyTo) => onPasswordChange(password,replyTo)
        case Get(replyTo) => onGet(replyTo)
        case DeleteProfile(replyTo) => onDelete(replyTo)
        case ValidatePassword(pw,replyTo) => onValidatePassword(pw,replyTo)
        case a => Effect.reply(a.replyTo)(ProfileCmdRejected("This command is unknown"))
      }
    }
  }

  private def onValidatePassword(pw: String, replyTo: ActorRef[ProfileConfirmation]): ReplyEffect[ProfileEvent,Profile] = {
    if(salt.map(new JBCryptPasswordEncoder(_)).getOrElse(new JBCryptPasswordEncoder()).matches(pw,passwordHash)) {
      Effect
        .reply(replyTo)( ProfileCmdAccepted(toSummary(this)) )
    } else
      Effect
      .reply(replyTo)( ProfileCmdRejected("Password is incorrect") )
  }

  private def onDelete(replyTo: ActorRef[ProfileConfirmation]): ReplyEffect[ProfileEvent,Profile] = {
    Effect
      .persist(Deleted(DateTime.now()))
      .thenReply(replyTo)(r => ProfileCmdAccepted(toSummary(r)))
  }

  private def onGet(replyTo: ActorRef[ProfileConfirmation]): ReplyEffect[ProfileEvent,Profile] = {
    Effect.reply(replyTo)( ProfileCmdAccepted( toSummary(this) ) )
  }

  private def onPasswordChange(pw: String,
                               replyTo: ActorRef[ProfileConfirmation]): ReplyEffect[ProfileEvent,Profile] = {
    val salt = org.mindrot.jbcrypt.BCrypt.gensalt(24)
    Effect
      .persist(PasswordChanged(new JBCryptPasswordEncoder(salt).encode(pw),Some(salt),DateTime.now()))
      .thenReply(replyTo)(r => ProfileCmdAccepted(toSummary(r) ) )
  }

  private def onAddRole(role: DefaultRoles,
                        replyTo: ActorRef[ProfileConfirmation]): ReplyEffect[ProfileEvent,Profile] = {
    if(roles.contains(role)){
      Effect.reply(replyTo)(ProfileCmdRejected("This roles is already assigned"))
    } else Effect
      .persist(RoleAdded(role,DateTime.now()))
      .thenReply(replyTo)(r => ProfileCmdAccepted( toSummary(r) ) )
  }

  private def onRemoveRole(role: DefaultRoles,
                         replyTo: ActorRef[ProfileConfirmation]): ReplyEffect[ProfileEvent,Profile] = {
    if(roles.contains(role))
      Effect
        .persist(RoleRemoved(role,DateTime.now()))
        .thenReply(replyTo)(r => ProfileCmdAccepted(toSummary(r)) )
    else
      Effect.reply(replyTo)(ProfileCmdRejected("Sorry cannot remove role, cause the role is not assigned"))
  }

  private def onCreate(login: String,
                       password: String,
                       email: Option[EmailAddress],
                       roles: Set[DefaultRoles],
                       name: PersonalData,
                       replyTo: ActorRef[ProfileConfirmation]): ReplyEffect[ProfileEvent,Profile] = {
    val salt = org.mindrot.jbcrypt.BCrypt.gensalt(14)
    val pw = new JBCryptPasswordEncoder(salt).encode(password)
    Effect
        .persist(
          ProfileCreated(
            login,
            pw,
            Some(salt),
            roles.toSeq,
            email,
            name,
            DateTime.now()
          )
        )
        .thenReply(replyTo)(t => ProfileCmdAccepted(toSummary(t)))
  }

  //COMMAND HANDLINGS



  // we don't make a distinction of checked or open for the event handler
  // because a checked-out cart will never persist any new event
  def applyEvent(evt: ProfileEvent): Profile = evt match {
    case x: ProfileCreated => onCreated(x)
    case _: Deleted => onDeleted
    case RoleAdded(role,_) => onRoleAdded(role)
    case RoleRemoved(role,_) => onRoleRemoved(role)
    case PasswordChanged(pw,salt,_) => onPasswordChanged(pw,salt)
  }

  private def onRoleAdded(role: DefaultRoles) = {
    copy(roles = this.roles ++ Seq(role) )
  }

  private def onRoleRemoved(role: DefaultRoles) = {
    copy(roles = this.roles.filterNot(_ == role))
  }

  private def onPasswordChanged(pw: String, salt: Option[String]) = copy(passwordHash = pw, salt = salt)

  private def onDeleted = copy(deleted = true)

  private def onCreated(x: ProfileCreated) = {
    copy(
      emailAddress = x.emailAddress,login = x.login,
      passwordHash = x.passwordHash,
      salt = x.salt, roles = x.roles,
      name = x.name
    )
  }


}



/**
 * Akka serialization, used by both persistence and remoting, needs to have
 * serializers registered for every type serialized or deserialized. While it's
 * possible to use any serializer you want for Akka messages, out of the box
 * Lagom provides support for JSON, via this registry abstraction.
 *
 * The serializers are registered here, and then provided to Lagom in the
 * application loader.
 */
object ProfileSerializerRegistry extends JsonSerializerRegistry {
  import Profile._

  override def serializers: Seq[JsonSerializer[_]] = Seq(
    //Entity und Commands
    JsonSerializer[Profile],
    JsonSerializer[ProfileCreated],
    JsonSerializer[RoleRemoved],
    JsonSerializer[RoleAdded],
    JsonSerializer[PasswordChanged],
    JsonSerializer[Deleted],
    //Replies!!
    JsonSerializer[ProfileSummary],
    JsonSerializer[ProfileCmdAccepted],
    JsonSerializer[ProfileCmdRejected],
    JsonSerializer[ProfileConfirmation]
  )
}
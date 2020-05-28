package de.heilpraktikerelbmarsch.security.api.profiles

import java.util.{Date, UUID}

import de.heilpraktikerelbmarsch.util.adt.security.DefaultRoles.{AdminRole, SuperAdminRole, SystemRole, TherapistRole}
import org.joda.time.DateTime
import org.pac4j.core.profile.CommonProfile

import scala.util.{Success, Try}


case class PanakeiaJWTProfile() extends CommonProfile {
  import scala.jdk.CollectionConverters._
  override def isExpired: Boolean = {
    val issuedDate = Try(new DateTime(this.getAttribute("iat").asInstanceOf[Date].getTime))
    val expireDate = Try(new DateTime(this.getAttribute("exp").asInstanceOf[Date].getTime))
    (issuedDate,expireDate) match {
      case (_,Success(exp)) => exp.isBeforeNow
      case (Success(issue),_) => issue.isBefore(DateTime.now().plusMillis(30))
      case _ => true
    }
  }

  def getServiceName: String = Try(getAttribute("Microservice-Name").asInstanceOf[String]).getOrElse("No Service")

  def getUserEntityId: Option[UUID] = Try(UUID.fromString(getAttribute("entityId").asInstanceOf[String])).toOption

  //Synonym für username
  def getUserId: String = Try(getAttribute("userId").asInstanceOf[String]).toOption.getOrElse("")

  def isSystemProfile: Boolean = {
    val set = this.getRoles.asScala.toSet
    Set(SystemRole.name).forall(e => set.contains(e))
  }

  def isSuperAdminProfile: Boolean = this.getRoles.asScala.toSet.contains(SuperAdminRole.name)

  def isAdminProfile: Boolean = this.getRoles.asScala.toSet.contains(AdminRole.name)

  def isTherapistProfile: Boolean = this.getRoles.asScala.toSet.contains(TherapistRole.name)

}

object PanakeiaJWTProfile {

  def apply(commonProfile: CommonProfile): PanakeiaJWTProfile = {
    val a = PanakeiaJWTProfile()
    a.addAttributes(commonProfile.getAttributes)
    a.setClientName(commonProfile.getClientName)
    a.setId(commonProfile.getId)
    a.setRoles(commonProfile.getRoles)
    a.setPermissions(commonProfile.getPermissions)
    a.setLinkedId(commonProfile.getLinkedId)
    a
  }

}

object AnonymousJWTProfile extends PanakeiaJWTProfile {
  setId("Anonymous")
}

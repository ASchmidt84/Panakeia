package de.heilpraktikerelbmarsch.binary.api.services

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceAcl, ServiceCall}
import de.heilpraktikerelbmarsch.util.adt.security.MicroServiceIdentifier.BinaryServiceIdentifier

trait BinaryService extends Service {

  def createBinaryEntry(): ServiceCall[NotUsed,NotUsed]

  override final def descriptor: Descriptor = {
    import Service._
    named(BinaryServiceIdentifier.name)
      .withCalls(
        //
      )
      .withAutoAcl(true)
      .withAcls(
        ServiceAcl(pathRegex = Some(path("file/patient")))
      )
  }

  private def path(x: String) = s"/api/${BinaryServiceIdentifier.path}/$x"

}

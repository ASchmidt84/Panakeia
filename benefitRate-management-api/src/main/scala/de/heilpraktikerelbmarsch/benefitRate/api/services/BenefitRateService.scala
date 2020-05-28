package de.heilpraktikerelbmarsch.benefitRate.api.services

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import de.heilpraktikerelbmarsch.benefitRate.api.request.{BenefitRateForm, RateForm}
import de.heilpraktikerelbmarsch.benefitRate.api.response.BenefitRateView
import de.heilpraktikerelbmarsch.util.adt.security.MicroServiceIdentifier.BenefitRateServiceIdentifier

trait BenefitRateService extends Service {

  def createBenefitRate(): ServiceCall[BenefitRateForm,BenefitRateView]

  def getBenefitRate(number: String): ServiceCall[NotUsed,BenefitRateView]

  def setRate(number: String): ServiceCall[RateForm,BenefitRateView]

  def deleteBenefitRate(number: String): ServiceCall[NotUsed,BenefitRateView]

  def size(): ServiceCall[NotUsed,Long]

  def list(take: Long = 100,
           drop: Long = 0): ServiceCall[NotUsed,Seq[BenefitRateView]]

  def search(query: String,
             take: Long,
             drop: Long): ServiceCall[NotUsed,Seq[BenefitRateView]]

  override final def descriptor: Descriptor = {
    import Service._
    named(BenefitRateServiceIdentifier.name)
      .withCalls(
        pathCall(path(":number"),getBenefitRate _),
        restCall(Method.DELETE,path(":number"), deleteBenefitRate _),
        restCall(Method.PUT,path(":number/rate"), setRate _),
        restCall(Method.POST,path(""),createBenefitRate _),
        pathCall(path("all/size"), size _ ),
        pathCall(path("all/list?take&drop"), list _),
        pathCall(path("all/search?query&take&drop"), search _)
      )
      .withAutoAcl(true)
  }

  private def path(x: String) = s"/api/${BenefitRateServiceIdentifier.path}/$x"

}

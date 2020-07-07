package de.heilpraktikerelbmarsch.benefitRate.api.response

import de.heilpraktikerelbmarsch.benefitRate.api.adt.Rate
import play.api.libs.json.{Json, OFormat}


/**
 * The benefit rate. Means a rate for a service. Exp 20.4 Teilmassage
 * @param feeNumber The GebüH number or a self given number Exp. 20.4
 * @param officialGebUEH If this a benefit defined by GebüH
 * @param alternativeForGOAE This rate is an alternative for an given GOÄ number
 * @param benefitDescription The description of the benefit Exp. Teilmassage
 * @param rates The rates are a sequence of fees for each settlement type like PKV1 or anything else
 */
case class BenefitRateView(feeNumber: String,
                           officialGebUEH:Boolean,
                           alternativeForGOAE: Option[String],
                           benefitDescription: String,
                           rates: Seq[Rate])

object BenefitRateView {
  import Rate._
  implicit val format: OFormat[BenefitRateView] = Json.format
}

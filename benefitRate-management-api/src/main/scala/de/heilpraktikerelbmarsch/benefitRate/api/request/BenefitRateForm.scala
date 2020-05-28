package de.heilpraktikerelbmarsch.benefitRate.api.request

import de.heilpraktikerelbmarsch.util.adt.benefits.SettlementType
import play.api.libs.json.{Json, OFormat}

/**
 * A form to create a new benefitRate
 * @param feeNumber Unique number
 * @param officialGebUEH is that a official GebüH
 * @param officialGOAE is that a alternative GOÄ
 * @param benefitDescription a short plain text description
 */
case class BenefitRateForm(feeNumber: String,
                           officialGebUEH:Boolean,
                           officialGOAE: Boolean,
                           benefitDescription: String)

object BenefitRateForm {
  implicit val format: OFormat[BenefitRateForm] = Json.format
}


case class RateForm(euro: Double,
                    settlementType: SettlementType)

object RateForm {
  import SettlementType._
  implicit val format: OFormat[RateForm] = Json.format
}
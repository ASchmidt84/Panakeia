package de.heilpraktikerelbmarsch.benefitRate.api.request

import de.heilpraktikerelbmarsch.util.adt.benefits.SettlementType
import play.api.libs.json.{Json, OFormat}

/**
 * A form to create a new benefitRate
 * @param feeNumber Unique number
 * @param officialGebUEH is that a official GebüH
 * @param alternativeForGOAE Some means that this is an alternative for an GOÄ Number
 * @param benefitDescription a short plain text description
 */
case class BenefitRateForm(feeNumber: String,
                           officialGebUEH:Boolean,
                           alternativeForGOAE: Option[String],
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
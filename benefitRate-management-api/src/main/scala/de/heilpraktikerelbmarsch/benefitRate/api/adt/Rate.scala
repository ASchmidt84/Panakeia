package de.heilpraktikerelbmarsch.benefitRate.api.adt

import de.heilpraktikerelbmarsch.util.adt.benefits.SettlementType
import play.api.libs.json.{Json, OFormat}
import squants.market.Money

case class Rate(fee: Money,
                settlementType: SettlementType)

object Rate {
  import de.heilpraktikerelbmarsch.util.converters.JsonFormatters._
  implicit val format: OFormat[Rate] = Json.format
}

package de.heilpraktikerelbmarsch.util.adt.contacts

import play.api.libs.json.{Format, Json}

case class Operator(id: String,
                    name: String)

object Operator {
  implicit val format: Format[Operator] = Json.format
}


class SystemOperator(name: String) extends Operator("SYSTEM",name)

object SystemOperator {

  def apply(name:String): SystemOperator = new SystemOperator(name)

  def apply(id: String, name: String) = new SystemOperator(name)

  def unapply(op: SystemOperator): Option[(String,String)] = {
    Some(op.id -> op.name)
  }

  implicit val format: Format[SystemOperator] = Json.format
}

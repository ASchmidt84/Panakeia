package de.heilpraktikerelbmarsch.util.converters

import java.text.SimpleDateFormat
import java.time.{Duration, Year}
import java.util.{Locale, UUID}

import com.google.common.net.MediaType
import org.apache.commons.lang3.LocaleUtils
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, LocalDate, Period}
import play.api.libs.json.{Format, JsError, JsObject, JsPath, JsString, JsSuccess, JsValue, Json, JsonValidationError, Reads, Writes, __}
import squants.market.{EUR, Money, defaultMoneyContext}
import squants.mass.Mass
import squants.space.Length
import squants.time.Time
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.util.Try

case object JsonFormatters {

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = Reads {
    case JsString(s) =>
      try {
        JsSuccess(enum.withName(s).asInstanceOf[E#Value])
      } catch {
        case _: NoSuchElementException =>
          JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not contain '$s'")
      }
    case _ => JsError("String value expected")
  }
  def enumWrites[E <: Enumeration]: Writes[E#Value] = Writes(v => JsString(v.toString))
  def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }

  def singletonReads[O](singleton: O): Reads[O] = {
    (__ \ "value").read[String].collect(
      JsonValidationError(s"Expected a JSON object with a single field with key 'value' and value '${singleton.getClass.getSimpleName}'")
    ) {
      case s if s == singleton.getClass.getSimpleName => singleton
    }
  }
  def singletonWrites[O]: Writes[O] = Writes { singleton =>
    Json.obj("value" -> singleton.getClass.getSimpleName)
  }
  def singletonFormat[O](singleton: O): Format[O] = {
    Format(singletonReads(singleton), singletonWrites)
  }

  implicit val uuidReads: Reads[UUID] = implicitly[Reads[String]]
    .collect(JsonValidationError("Invalid UUID"))(Function.unlift { str =>
      Try(UUID.fromString(str)).toOption
    })
  implicit val uuidWrites: Writes[UUID] = Writes { uuid =>
    JsString(uuid.toString)
  }

  implicit val uuidFormat: Format[UUID] = Format(uuidReads,uuidWrites)

  implicit val durationReads: Reads[Duration] = implicitly[Reads[String]]
    .collect(JsonValidationError("Invalid duration"))(Function.unlift { str =>
      Try(Duration.parse(str)).toOption
    })
  implicit val durationWrites: Writes[Duration] = Writes { duration =>
    JsString(duration.toString)
  }

  implicit val durationFormats: Format[Duration] = Format(durationReads,durationWrites)

  implicit val mediaTypeReads: Reads[MediaType] = implicitly[Reads[String]]
    .collect(JsonValidationError("Invalid MediaType/MimeType"))(Function.unlift{str =>
      Try( MediaType.parse(str) ).toOption
    })

  implicit val mediaTypeWrites: Writes[MediaType] = Writes{data =>
    JsString(data.toString)
  }

  implicit val jodaDateTimeWrites: Writes[DateTime] = {r => Json.toJson( r.toString(DateTimeFormat.longDateTime()) ) }
  /*
shortDate:         11/3/16
shortDateTime:     11/3/16 4:25 AM
mediumDate:        Nov 3, 2016
mediumDateTime:    Nov 3, 2016 4:25:35 AM
longDate:          November 3, 2016
longDateTime:      November 3, 2016 4:25:35 AM MDT
fullDate:          Thursday, November 3, 2016
fullDateTime:      Thursday, November 3, 2016 4:25:35 AM Mountain Daylight Time
   */

  implicit val jodaDateTimeReads: Reads[DateTime] = (r: JsValue) => {
    //    val formatter = DateTimeFormat.forPattern("dd. MMM yyyy HH:mm:ss z").withLocale(Locale.GERMANY)
    val formatter = new SimpleDateFormat("MMM d, yyyy H:mm:ss a z")//E, dd MMM yyyy HH:mm:ss Z
    Try( new DateTime(formatter.parse(r.as[String])) )
      .map(result => JsSuccess(result))
      .recover(r => JsError(r.toString)).get
    //      .getOrElse(JsError("Impossible to create Joda DateTime"))
  }

  implicit val jodaDateTimeFormat: Format[DateTime] = Format(jodaDateTimeReads,jodaDateTimeWrites)

  implicit val jodaLocalDateWrites: Writes[LocalDate] = {r => Json.toJson( r.toString("dd.MM.yyyy") ) }

  implicit val jodaLocalDateReads: Reads[LocalDate] = (r: JsValue) => {
    Try( LocalDate.parse(r.as[String],DateTimeFormat.forPattern("dd.MM.yyyy")) )
      .map(result => JsSuccess(result))
      .recover(r => JsError(r.toString)).get
  }

  implicit val jodaLocalDateFormat: Format[DateTime] = Format(jodaLocalDateReads,jodaLocalDateWrites)

  implicit val moneyTypeFormat: Format[Money] = (
    (JsPath \ "value").format[BigDecimal] and
      (JsPath \ "currency").format[String]
    )( (decimal,cur) => Money(s"$decimal $cur")(defaultMoneyContext).getOrElse(Money(decimal,EUR)), (e) => (e.amount,e.currency.code) )

  implicit val massWrites: Writes[Mass] = Writes{ mass =>
    Json.obj("unit"-> mass.unit.symbol, "value" -> mass.value )
  }

  implicit val massReads: Reads[Mass] = (json:JsValue) => {
    val js = json.as[JsObject]
    Mass( js("value").as[Double] -> js("unit").as[String] ).map(e => JsSuccess(e)).getOrElse(JsError("cannot convert to mass"))
  }

  implicit val lengthWrites: Writes[Length] = Writes{ length =>
    Json.obj("unit" -> length.unit.symbol, "value" -> length.value )
  }

  implicit val lengthReads: Reads[Length] = (json: JsValue) => {
    val js = json.as[JsObject]
    Length(js("value").as[Double] -> js("unit").as[String]).map(e => JsSuccess(e)).getOrElse(JsError("cannot convert to length"))
  }

  implicit val timeWrites: Writes[Time] = Writes{ time =>
    Json.obj("unit" -> time.unit.symbol, "value" -> time.value )
  }

  implicit val timeReads: Reads[Time] = (json: JsValue) => {
    val js = json.as[JsObject]
    Time(js("value").as[Double] -> js("unit").as[String]).map(e => JsSuccess(e)).getOrElse(JsError("cannot convert to time"))
  }

  implicit val yearWrites: Writes[Year] = Writes{ year =>
    Json.toJson(year.getValue)
  }

  implicit val yearReads: Reads[Year] = (json: JsValue) => {
    JsSuccess(Year.of(json.as[Int]))
  }

  implicit val scalaDurationWrites: Writes[scala.concurrent.duration.Duration] = Writes{ duration =>
    Json.toJson(duration.toString)
  }

  implicit val scalaDurationReads: Reads[scala.concurrent.duration.Duration] = (json: JsValue) => {
    JsSuccess(scala.concurrent.duration.Duration(json.as[String]))
  }

  implicit val localeWrites: Writes[Locale] = Writes{locale =>
    Json.toJson(Map("lang" -> Json.toJson(locale.getLanguage),"country" -> Json.toJson(locale.getCountry)))
  }

  implicit val localeReads: Reads[Locale] = (json: JsValue) => {
    val js = json.as[JsObject]
    Try{
      LocaleUtils.toLocale(s"${js("lang").as[String].toLowerCase}_${js("country").as[String].toUpperCase}")
    }.map(r => JsSuccess(r)).getOrElse(JsError("impossible to convert this locale"))
  }

  implicit val localeFormat: Format[Locale] = Format(localeReads,localeWrites)

  implicit val periodWrites: Writes[Period] = Writes{period =>
    val map = Map(
      "years" -> Json.toJson(period.getYears),
      "months" -> Json.toJson(period.getMonths),
      "weeks" -> Json.toJson(period.getWeeks),
      "days" -> Json.toJson(period.getDays),
      "hours" -> Json.toJson(period.getHours),
      "minutes" -> Json.toJson(period.getMinutes),
      "seconds" -> Json.toJson(period.getSeconds),
      "millis" -> Json.toJson(period.getMillis)
    )
    Json.toJson(map)
  }

  implicit val periodReads: Reads[Period] = (json: JsValue) => {
    val js = json.as[JsObject]
    Try{
      new Period()
        .withYears(js("years").as[Int])
        .withMonths(js("months").as[Int])
        .withWeeks(js("weeks").as[Int])
        .withDays(js("days").as[Int])
        .withHours(js("hours").as[Int])
        .withMinutes(js("minutes").as[Int])
        .withSeconds(js("seconds").as[Int])
        .withMillis(js("millis").as[Int])
      //      LocaleUtils.toLocale(s"${js("lang").as[String].toLowerCase}_${js("country").as[String].toUpperCase}")
    }.map(r => JsSuccess(r)).getOrElse(JsError("impossible to convert this to period"))
  }

  implicit val periodFormat: Format[Period] = Format(periodReads,periodWrites)

}
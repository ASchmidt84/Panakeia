package de.heilpraktikerelbmarsch.util.functions


import org.joda.time.{DateTime, Years}

object DateFunction {

  def yearsBetweenNow(date: DateTime): Int = {
    Years.yearsBetween(date,DateTime.now()).getYears
  }

}

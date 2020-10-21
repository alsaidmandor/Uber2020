package com.example.kotlinuberremake

import com.example.kotlinuberremake.model.DriverInfoModel
import java.lang.StringBuilder

object Common
{
    fun buildWelcomeMessage(): String
    {
        return StringBuilder(currentUser!!.firstName)
            .append("Welcome")
            .append(currentUser!!.lastName)
            .toString()
    }

    const val DRIVERS_LOCATION_REFERENCE: String = "DriversLocation"
    var currentUser: DriverInfoModel?= null
    const val DRIVER_INFO_REFERENCE: String = "DriverInfo"
}
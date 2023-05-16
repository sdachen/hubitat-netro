/**
 *
 *  Hubitat Netro Whisperer Smart Plant Sensor Integration
 *  Copyright 2022 Scott Deeann Chen 
 *
 */ 

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method.*
import groovyx.net.http.ContentType

metadata {
    definition (name: "Netro Whisperer Smart Plant Sensor", namespace: "sdachen.netrowhisperer", author: "Scott Deeann Chen") {
        
        capability "TemperatureMeasurement"
        capability "IlluminanceMeasurement"

        command "refresh"

        attribute "dataTimestampUtc", "string"
        attribute "moisture", "double"
        attribute "batteryLevel", "double"
        attribute "isActiveToday", "boolean"
    }

    preferences {
        input name: "pollingInterval", type: "decimal", title: "Polling Interval (Minutes)", required: true, default: 5
        input name: "serialNumber", type: "text", title: "Serial Number", required: true
        input name: "cfdecider", type: "bool", title: "Temperature Unit (Check: C, Uncheck: F)"
        input name: "enableLogging", type: "bool", title: "Enable Debugging Logging"
    }
}

// Hubitat device methods
void installed() {
    if (enableLogging) log.debug "installed()"
}

void updated() {
    if (enableLogging) log.debug "updated()"
    asyncGetSensorData()
}

void refresh() {
    if (enableLogging) log.debug "refresh()"
    asyncGetSensorData()
}

void asyncGetSensorData() {
    if (enableLogging) log.debug "asyncGetSensorData()"

    def today = new Date(now())
    def todayString = today.format("yyyy-MM-dd")

    Map query = [
        key: serialNumber,
        start_date: todayString
    ]
    Map params = [
        uri: "https://api.netrohome.com",
        path: "/npa/v1/sensor_data.json",
        query: query]

    asynchttpGet("getSensorDataCallback", params)
    runIn((int)pollingInterval * 60, asyncGetSensorData)
}

void getSensorDataCallback(response, responseData) {
    if (enableLogging) log.debug "getSensorDataCallback()"
    if (response.getStatus() == 200) {
        def json = response.getJson()
        def data = json.data.sensor_data

        if (data.size > 0) {
            def lastData = data[0]
            def lastDateTime = toDateTime(lastData.time + "Z")
            
            for (int i = 0; i < data.size; ++i) {
                def dateTime = toDateTime(data[i].time + "Z")
                if (lastDateTime.before(dateTime)) {
                    lastDateTime = dateTime
                    lastData = data[i]
                }
            }

            sendEvent(name: "dataTimestampUtc", value: lastData.time, descriptionText: "Last Updated UTC", isStateChange: false)
            sendEvent(name: "moisture", value: lastData.moisture, descriptionText: "Relative Moisture %", isStateChange: false)
            sendEvent(name: "illuminance", value: lastData.sunlight, descriptionText: "Sunlight (Lux)", isStateChange: false)
            if (cfdecider) {
                sendEvent(name: "temperature", value: lastData.celsius, descriptionText: "Temperature (C)", isStateChange: false)
            } else {
                sendEvent(name: "temperature", value: lastData.fahrenheit, descriptionText: "Temperature (F)", isStateChange: false)

            }
            sendEvent(name: "batteryLevel", value: lastData.battery_level, descriptionText: "Battery Level", isStateChange: false)
            sendEvent(name: "isActiveToday", value: true, descriptionText: "Has data last 24 hours.", isStateChange: false)
        } else {
            sendEvent(name: "isActiveToday", value: false, descriptionText: "No data last 24 hours.", isStateChange: false)
        }

    } else {
        if (enableLogging) log.debug "Request Error!"
        sendEvent(name: "isActiveToday", value: false, descriptionText: "Request Error.", isStateChange: false)
    }
}
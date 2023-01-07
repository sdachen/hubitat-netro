/**
 *
 *  Hubitat Netro Pixie Smart Hose Faucet Timer Integration
 *  Copyright 2022 Scott Deeann Chen 
 *
 */ 

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

import groovyx.net.http.HttpResponseException
import groovyx.net.http.Method.*
import groovyx.net.http.ContentType

metadata {
    definition (name: "Netro Pixie Smart Hose Faucet Timer", namespace: "sdachen.netro", author: "Scott Deeann Chen") {

        command "refresh"
        command "water"
        command "cancelAll"
        command "stopWatering"

        attribute "batteryLevel", "double"
        attribute "isActiveToday", "boolean"
        attribute "scheduleSource", "string"
        attribute "nextStartTimeUtc", "string"
        attribute "nextEndTimeUtc", "string"
        attribute "nextStartTimeLocal", "string"
        attribute "nextEndTimeLocal", "string"

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
    asyncGetSchedulesData()
}

void water() {
    if (enableLogging) log.debug "water()"
}

void cancelAll() {
    if (enableLogging) log.debug "cancelAll()"
}

void stopWatering() {
    if (enableLogging) log.debug "stopWatering()"
}

@Field static final String netroURI = "https://api.netrohome.com"

void asyncGetSchedulesData() {
    if (enableLogging) log.debug "asyncGetSchedulesData()"

    def today = new Date(now())
    def todayString = today.format("yyyy-MM-dd")

    Map query = [
        key: serialNumber,
        start_date: todayString
    ]
    Map params = [
        uri: netroURI,
        path: "/npa/v1/schedules.json",
        query: query]

    asynchttpGet("getScheduleCallback", params)
    runIn((int)pollingInterval * 60, asyncGetSensorData)
}

void getScheduleCallback(response, responseData) {
    if (enableLogging) log.debug "getScheduleCallback()"
    if (response.getStatus() == 200) {
        def json = response.getJson()
        def data = json.data.schedules

        if (data.size > 0) {
            def lastData = data[0]
            def lastDateTime = toDateTime(lastData.start_time + "Z")
            
            for (int i = 0; i < data.size; ++i) {
                def dateTime = toDateTime(data[i].start_time + "Z")
                if (lastDateTime.after(dateTime)) {
                    lastDateTime = dateTime
                    lastData = data[i]
                }
            }

            sendEvent(name: "nextStartTimeUtc", value: lastData.start_time, descriptionText: "Next Start Time UTC", isStateChange: false)
            sendEvent(name: "nextEndTimeUtc", value: lastData.end_time, descriptionText: "Next End Time UTC", isStateChange: false)
            sendEvent(name: "nextStartTimeLocal", value: lastData.local_start_time, descriptionText: "Next Start Time Local", isStateChange: false)
            sendEvent(name: "nextEndTimeLocal", value: lastData.local_end_time, descriptionText: "Next End Time Local", isStateChange: false)
            sendEvent(name: "scheduleSource", value: lastData.source, descriptionText: "Schedule source", isStateChange: false)
        } else {
            sendEvent(name: "isActiveToday", value: false, descriptionText: "No data last 24 hours.", isStateChange: false)
        }

    } else {
        if (enableLogging) log.debug "Request Error!"
        sendEvent(name: "isActiveToday", value: false, descriptionText: "Request Error.", isStateChange: false)
    }
}
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

        // Add capability so that device.getSupportedAttributes() does not show up as empty.
        // This is useful for InfluxDB Logger integration.
        capability "Sensor"

        command "refresh"
        command "waterMinutes", ["number"]
        command "pauseWateringDays", ["number"]
        command "cancelManualSchedules"

        attribute "batteryLevel", "double"
        attribute "nextScheduleSource", "string"
        attribute "nextStartTimeUtc", "string"
        attribute "nextEndTimeUtc", "string"
        attribute "nextStartTimeLocal", "string"
        attribute "nextEndTimeLocal", "string"
        attribute "nextDateLocal", "string"

        attribute "lastScheduleSource", "string"
        attribute "lastStartTimeUtc", "string"
        attribute "lastEndTimeUtc", "string"
        attribute "lastStartTimeLocal", "string"
        attribute "lastEndTimeLocal", "string"
        attribute "lastScheduleStatus", "string"
        attribute "lastDateLocal", "string"
        attribute "lastWateringTimeString", "string"
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
    asyncGetSchedules()
}

void waterMinutes(BigDecimal duration) {
    if (enableLogging) log.debug "water()"
    asyncPostWater(duration)
}

void pauseWateringDays(BigDecimal days) {
    if (enableLogging) log.debug "pauseWateringDays()"
    asyncPostNoWater(days)
}

void cancelManualSchedules() {
    if (enableLogging) log.debug "cancelManualSchedules()"
    asyncPostStopWater()
}

@Field static final String netroURI = "https://api.netrohome.com"

void asyncGetSchedules() {
    if (enableLogging) log.debug "asyncGetSchedules()"

    Map query = [
        key: serialNumber
    ]
    Map params = [
        uri: netroURI,
        path: "/npa/v1/schedules.json",
        query: query]

    asynchttpGet("parseResponseSchedule", params)
    runIn((int)pollingInterval * 60, asyncGetSchedules)
}

void parseResponseSchedule(response, responseData) {
    if (enableLogging) log.debug "getScheduleCallback()"
    if (enableLogging) log.debug response.getStatus()
    if (response.getStatus() == 200) {
        def currentTime = new Date(now())

        def json = response.getJson()
        def nextSchedules = json.data.schedules.findAll { toDateTime(it.start_time + "Z").after(currentTime) }
        
        if (nextSchedules.size > 0) {
            
            def firstSchedule = nextSchedules[0]
            def firstDateTime = toDateTime(firstSchedule.start_time + "Z")
            
            for (int i = 0; i < nextSchedules.size; ++i) {
                def dateTime = toDateTime(nextSchedules[i].start_time + "Z")
                if (firstDateTime.after(dateTime)) {
                    firstDateTime = dateTime
                    firstSchedule = nextSchedules[i]
                }
            }

            sendEvent(name: "nextStartTimeUtc", value: firstSchedule.start_time, descriptionText: "Next Start Time UTC", isStateChange: false)
            sendEvent(name: "nextEndTimeUtc", value: firstSchedule.end_time, descriptionText: "Next End Time UTC", isStateChange: false)
            sendEvent(name: "nextStartTimeLocal", value: firstSchedule.local_start_time, descriptionText: "Next Start Time Local", isStateChange: false)
            sendEvent(name: "nextEndTimeLocal", value: firstSchedule.local_end_time, descriptionText: "Next End Time Local", isStateChange: false)
            sendEvent(name: "nextScheduleSource", value: firstSchedule.source, descriptionText: "Schedule source", isStateChange: false)
            sendEvent(name: "nextDateLocal", value: firstSchedule.local_date, descriptionText: "Next local date.", isStateChange: false)
        }

        def prevSchedules = json.data.schedules.findAll { toDateTime(it.start_time + "Z").before(currentTime) && it.status == "EXECUTED"}
        if (prevSchedules.size > 0) {
            
            def lastSchedule = prevSchedules[0]
            def lastDateTime = toDateTime(lastSchedule.start_time + "Z")

            for (int i = 0; i < prevSchedules.size; ++i) {
                def dateTime = toDateTime(prevSchedules[i].start_time + "Z")
                if (lastDateTime.before(dateTime)) {
                    lastDateTime = dateTime
                    lastSchedule = prevSchedules[i]
                }
            }

            def lastWateringTimeString = lastSchedule.local_date + "_" + lastSchedule.local_start_time.substring(0,5) + "-" + lastSchedule.local_end_time.substring(0,5)

            sendEvent(name: "lastStartTimeUtc", value: lastSchedule.start_time, descriptionText: "Last Start Time UTC", isStateChange: false)
            sendEvent(name: "lastEndTimeUtc", value: lastSchedule.end_time, descriptionText: "Last End Time UTC", isStateChange: false)
            sendEvent(name: "lastStartTimeLocal", value: lastSchedule.local_start_time, descriptionText: "Last Start Time Local", isStateChange: false)
            sendEvent(name: "lastEndTimeLocal", value: lastSchedule.local_end_time, descriptionText: "Last End Time Local", isStateChange: false)
            sendEvent(name: "lastScheduleSource", value: lastSchedule.source, descriptionText: "Schedule source", isStateChange: false)
            sendEvent(name: "lastScheduleStatus", value: lastSchedule.status, descriptionText: "Schedule status", isStateChange: false)
            sendEvent(name: "lastDateLocal", value: lastSchedule.local_date, descriptionText: "Last local date.", isStateChange: false)
            sendEvent(name: "lastWateringTimeString", value: lastWateringTimeString, descriptionText: "Last time string.", isStateChange: false)
            

            lastScheduleStatus
        }

    } else {
        if (enableLogging) log.debug "Request failed."
    }
}

void asyncPostWater(BigDecimal duration) {
    if (enableLogging) log.debug "asyncPostWater()"
    
    Map data = [
        key: serialNumber, 
        duration: duration]

    Map params = [
        uri: netroURI,
        path: "/npa/v1/water.json",
        requestContentType: 'application/json',
		contentType: 'application/json',
        body: data]
    
    asynchttpPost("parseResponseSchedule", params)
}

void asyncPostStopWater() {
    if (enableLogging) log.debug "asyncPostStopWater()"
    
    Map data = [
        key: serialNumber]

    Map params = [
        uri: netroURI,
        path: "/npa/v1/stop_water.json",
        requestContentType: 'application/json',
		contentType: 'application/json',
        body: data]
    
    asynchttpPost("callbackGetSchedules", params)
}

void asyncPostNoWater(BigDecimal days) {
    if (enableLogging) log.debug "asyncPostStopWater()"
    
    Map data = [
        key: serialNumber,
        days: days]

    Map params = [
        uri: netroURI,
        path: "/npa/v1/no_water.json",
        requestContentType: 'application/json',
		contentType: 'application/json',
        body: data]
    
    asynchttpPost("callbackGetSchedules", params)
}

void callbackGetSchedules(response, data) {
    asyncGetSchedules()

}


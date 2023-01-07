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
        command "waterMinutes", ["number"]
        command "pauseWateringDays", ["number"]
        command "cancelManualSchedules"

        attribute "batteryLevel", "double"
        attribute "isActiveToday", "boolean"
        attribute "scheduleSource", "string"
        attribute "nextStartTimeUtc", "string"
        attribute "nextEndTimeUtc", "string"
        attribute "nextStartTimeLocal", "string"
        attribute "nextEndTimeLocal", "string"
        attribute "nextDateLocal", "string"

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

    asynchttpGet("parseResponseSchedule", params)
    runIn((int)pollingInterval * 60, asyncGetSchedules)
}

void parseResponseSchedule(response, responseData) {
    if (enableLogging) log.debug "getScheduleCallback()"
    if (enableLogging) log.debug response.getStatus()
    if (response.getStatus() == 200) {
        def currentTime = new Date(now())

        def json = response.getJson()
        def schedules = json.data.schedules.findAll { toDateTime(it.start_time + "Z").after(currentTime) }
        
        if (schedules.size > 0) {
            
            def firstSchedule = schedules[0]
            def firstDateTime = toDateTime(firstSchedule.start_time + "Z")
            
            for (int i = 0; i < schedules.size; ++i) {
                def dateTime = toDateTime(schedules[i].start_time + "Z")
                if (firstDateTime.after(dateTime)) {
                    firstDateTime = dateTime
                    firstSchedule = schedules[i]
                }
            }

            sendEvent(name: "nextStartTimeUtc", value: firstSchedule.start_time, descriptionText: "Next Start Time UTC", isStateChange: false)
            sendEvent(name: "nextEndTimeUtc", value: firstSchedule.end_time, descriptionText: "Next End Time UTC", isStateChange: false)
            sendEvent(name: "nextStartTimeLocal", value: firstSchedule.local_start_time, descriptionText: "Next Start Time Local", isStateChange: false)
            sendEvent(name: "nextEndTimeLocal", value: firstSchedule.local_end_time, descriptionText: "Next End Time Local", isStateChange: false)
            sendEvent(name: "scheduleSource", value: firstSchedule.source, descriptionText: "Schedule source", isStateChange: false)
            sendEvent(name: "nextDateLocal", value: firstSchedule.local_date, descriptionText: "Next local date.", isStateChange: false)
            sendEvent(name: "isActiveToday", value: true, descriptionText: "Data available.", isStateChange: false)
        } else {
            sendEvent(name: "isActiveToday", value: false, descriptionText: "No data.", isStateChange: false)
        }
    } else {
        if (enableLogging) log.debug "Request Error!"
        sendEvent(name: "isActiveToday", value: false, descriptionText: "Request Error.", isStateChange: false)
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
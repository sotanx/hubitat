/**
 *  Code derived from
 *  Source: https://github.com/kris2k2/hubitat/drivers/kris2k2-Sinope-TH112XZB.groovy
 */

#include helperLib.utils

metadata {

    definition(name: "Sinope TH112XZB Thermostat", namespace: "hubitat", author: "David Hebert") {
        capability "Initialize"
        capability "Configuration"
        capability "TemperatureMeasurement"
        capability "Thermostat"
        capability "Refresh"
        capability "PowerMeter"
        capability "EnergyMeter"
        
        // Receiving temperature notifications via RuleEngine
        capability "Notification"
        
        command "eco"
        command "resetPower"

        preferences {
            input name: "prefDisplayOutdoorTemp", type: "bool", title: "Enable display of outdoor temperature", defaultValue: true
            input name: "prefDisplayClock", type: "bool", title: "Enable display of clock", defaultValue: true
            input name: "prefDisplayBacklight", type: "bool", title: "Enable display backlight", defaultValue: true
            input name: "prefKeyLock", type: "bool", title: "Enable keylock", defaultValue: false
            input name: "enablePower", type: "bool", title: "Enable powermeter", defaultValue: false
            input "debugEnabled", "bool", required: true, title: "Enable debug logging", defaultValue: false
        }        

        fingerprint profileId: "0104", deviceId: "119C", manufacturer: "Sinope Technologies", model: "TH1123ZB", deviceJoinName: "TH1123ZB"
        fingerprint profileId: "0104", deviceId: "119C", manufacturer: "Sinope Technologies", model: "TH1124ZB", deviceJoinName: "TH1124ZB"
    }
}

//-- Installation ----------------------------------------------------------------------------------------

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def uninstalled() {
    unschedule()
}

def initialize() {
	trace("initialize", "info");
    unschedule();
    resetPower();
    configure();
    runEvery3Hours(configure)
    if ( enablePower == true ) {
        powerRefresh();
    }
}

//-- Parsing ---------------------------------------------------------------------------------------------

// parse events into attributes
def parse(String description) {
    def result = []
    def scale = getTemperatureScale()
    state.scale = scale
    def cluster = zigbee.parse(description)
    trace("parse: ${description}", "debug")
    if (description?.startsWith("read attr -")) {
        def descMap = zigbee.parseDescriptionAsMap(description)
        result += createCustomMap(descMap)
        if(descMap.additionalAttrs){
            def mapAdditionnalAttrs = descMap.additionalAttrs
            mapAdditionnalAttrs.each{add ->
                add.cluster = descMap.cluster
                result += createCustomMap(add)
            }
        }
    }
    
    return result
}

private createCustomMap(descMap){
    def result = []
    def map = [: ]

    if (descMap.cluster == "0201" && descMap.attrId == "0000") {
        map.name = "temperature"
        map.value = getTemperature(descMap.value)
    } else if (descMap.cluster == "0201" && descMap.attrId == "0008") {
        map.name = "thermostatOperatingState"
        def value = getHeatingDemand(descMap.value)
        if ( value < 10 ) {
            map.value = "idle";
            state.heating = false;
        } else {
            map.value = "heating";
            state.heating = true;
        }
    } else if (descMap.cluster == "0201" && descMap.attrId == "0012") {
        map.name = "heatingSetpoint"
        map.value = getTemperature(descMap.value)
    } else if (descMap.cluster == "0201" && descMap.attrId == "0015") {
        map.name = "heatingSetpointRangeLow"
        map.value = getTemperature(descMap.value)
    } else if (descMap.cluster == "0201" && descMap.attrId == "0016") {
        map.name = "heatingSetpointRangeHigh"
        map.value = getTemperature(descMap.value)
    } else if (descMap.cluster == "0201" && descMap.attrId == "001C") {
        map.name = "thermostatMode"
        map.value = getModeMap()[descMap.value]
    } else if (descMap.cluster == "0204" && descMap.attrId == "0001") {
        map.name = "thermostatLock"
        map.value = getLockMap()[descMap.value]
    } else if (descMap.cluster == "0B04" && descMap.attrId == "050B") {
        map.name = "power"
        map.value = getActivePower(descMap.value)
        computeEnergy(map.value);
        if ( enablePower == true ) {
            result << createEvent(name: "energy", value:state.energy.setScale(2, BigDecimal.ROUND_HALF_UP))
            result << createEvent(name: "energyDuration", value:state.energyDuration)
            result << createEvent(name: "totalHeatingRunTime", value:"${state.totalRunTime.setScale(2, BigDecimal.ROUND_HALF_UP)} minutes")
        }
    }
        
    if (map) {
        def isChange = isStateChange(device, map.name, map.value.toString())
        trace("${map.name}: ${map.value} (${descMap})", "debug")
        map.displayed = isChange
        if ((map.name.toLowerCase().contains("temp")) || (map.name.toLowerCase().contains("setpoint"))) {
            map.scale = state.scale
        }
        result << createEvent(map)
    }
    return result
}

/////////////////////////////////////////////////////////////////////////////
// Capabilities
/////////////////////////////////////////////////////////////////////////////

def refresh() {
    trace("refresh", "debug");
    
    def cmds = []    
    cmds += zigbee.readAttribute(0x0201, 0x0000) //Read Local Temperature
    cmds += zigbee.readAttribute(0x0201, 0x0008) //Read PI Heating State  
    cmds += zigbee.readAttribute(0x0201, 0x0012) //Read Heat Setpoint
    cmds += zigbee.readAttribute(0x0201, 0x001C) //Read System Mode
    cmds += zigbee.readAttribute(0x0201, 0x401C, [mfgCode: "0x1185"]) //Read System Mode
    cmds += zigbee.readAttribute(0x0204, 0x0000) //Read Temperature Display Mode
    cmds += zigbee.readAttribute(0x0204, 0x0001) //Read Keypad Lockout
    cmds += zigbee.readAttribute(0x0B04, 0x050B) //Read thermostat Active power
    sendZigbeeCommands(cmds)
}   

def powerRefresh() {
    trace("powerRefresh", "debug");
    def cmds = []    
    cmds += zigbee.readAttribute(0x0201, 0x0000) //Read Local Temperature
    cmds += zigbee.readAttribute(0x0201, 0x0008) //Read PI Heating State  
    cmds += zigbee.readAttribute(0x0B04, 0x050B) //Read thermostat Active power
    sendZigbeeCommands(cmds)
    if ( enablePower == true ) {
        runIn(30, powerRefresh);
    }
}

def configure(){    
    trace("configure", "debug");
        
    // Set unused default values
    sendEvent(name:"coolingSetpoint", value:getTemperature("0BB8")) // 0x0BB8 =  30 Celsius
    sendEvent(name:"thermostatFanMode", value:"auto") // We dont have a fan, so auto is is
    updateDataValue("lastRunningMode", "heat") // heat is the only compatible mode for this device

    // Prepare our zigbee commands
    def cmds = []

    // Configure Reporting
    cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 19, 301, 50)    //local temperature
    cmds += zigbee.configureReporting(0x0201, 0x0008, 0x0020, 4, 300, 10)   //PI heating demand
    cmds += zigbee.configureReporting(0x0201, 0x0012, 0x0029, 15, 302, 40)  //occupied heating setpoint    
    cmds += zigbee.configureReporting(0x0204, 0x0000, 0x30, 1, 0)           //Attribute ID 0x0000 = temperature display mode, Data Type: 8 bits enum
    cmds += zigbee.configureReporting(0x0204, 0x0001, 0x30, 1, 0)           //Attribute ID 0x0001 = keypad lockout, Data Type: 8 bits enum
    cmds += zigbee.configureReporting(0x0B04, 0x050B, DataType.INT16, 30, 599, 0x64) //Thermostat power draw
    
    // Configure displayed scale
    if (getTemperatureScale() == 'C') {
        cmds += zigbee.writeAttribute(0x0204, 0x0000, 0x30, 0)    // Wr °C on thermostat display
    } else {
        cmds += zigbee.writeAttribute(0x0204, 0x0000, 0x30, 1)    // Wr °F on thermostat display 
    }

    // Configure keylock
    if (prefKeyLock) {
        cmds+= zigbee.writeAttribute(0x204, 0x01, 0x30, 0x01) // Lock Keys
    } else {
        cmds+= zigbee.writeAttribute(0x204, 0x01, 0x30, 0x00) // Unlock Keys
    }

    // Configure Outdoor Weather
    if (prefDisplayOutdoorTemp) {
        cmds += zigbee.writeAttribute(0xFF01, 0x0011, 0x21, 10800)  //set the outdoor temperature timeout to 3 hours
    } else {
        cmds += zigbee.writeAttribute(0xFF01, 0x0011, 0x21, 0)  //set the outdoor temperature timeout immediately
    }     
        
    // Configure Screen Brightness
    if(prefDisplayBacklight){
        cmds += zigbee.writeAttribute(0x0201, 0x0402, 0x30, 0x0001) // set display brigtness to explicitly on       
    } else {
        cmds += zigbee.writeAttribute(0x0201, 0x0402, 0x30, 0x0000) // set display brightnes to ambient lighting
    }
    
    // Configure Clock Display
    if (prefDisplayClock) { 
        //To refresh the time        
        def d = new Date()
        int curHourSeconds = (d.hours * 60 * 60) + (d.minutes * 60) + d.seconds
        cmds += zigbee.writeAttribute(0xFF01, 0x0020, 0x23, curHourSeconds, [mfgCode: "0x119C"])
    } else {
        cmds += zigbee.writeAttribute(0xFF01, 0x0020, 0x23, -1) // set clock to -1 means hide the clock
    }

    // Submit zigbee commands
    sendZigbeeCommands(cmds)
    
    // Submit refresh
    refresh()
    
    // Return
    return
}

def auto() {
    heat()
}

def cool() {
    eco()
}

def emergencyHeat() {
    heat()
}

def heat() {
    trace("heat(): mode set", "info")
    def cmds = []
    cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 04, [:], 1000) // MODE
    cmds += zigbee.writeAttribute(0x0201, 0x401C, 0x30, 04, [mfgCode: "0x1185"]) // SETPOINT MODE
    sendZigbeeCommands(cmds)
}

def off() {
    trace("off(): mode set", "info");
    def cmds = []
    cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0)
    sendZigbeeCommands(cmds)    
}

def setHeatingSetpoint(preciseDegrees) {
    if (preciseDegrees != null) {
        def temperatureScale = getTemperatureScale()
        def degrees = new BigDecimal(preciseDegrees).setScale(1, BigDecimal.ROUND_HALF_UP)
        def cmds = []        
        trace("setHeatingSetpoint(${degrees}:${temperatureScale})", "info")
        def celsius = (temperatureScale == "C") ? degrees as Float : (fahrenheitToCelsius(degrees) as Float).round(2)
        int celsius100 = Math.round(celsius * 100)
        cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, celsius100) //Write Heat Setpoint
        sendZigbeeCommands(cmds)         
    } 
}

def setThermostatMode(String value) {
    trace("setThermostatMode(${value})", "info");
    def currentMode = device.currentState("thermostatMode")?.value
    def lastTriedMode = state.lastTriedMode ?: currentMode ?: "heat"
    def modeNumber;
    Integer setpointModeNumber;
    def modeToSendInString;
    switch (value) {
        case "heat":
        case "emergency heat":
        case "auto":
            return heat()
        
        case "eco":
        case "cool":
            return eco()
        
        default:
            return off()
    }
}

def eco() {
    trace("eco()", "info")
    def cmds = []
    cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 04, [:], 1000) // MODE
    cmds += zigbee.writeAttribute(0x0201, 0x401C, 0x30, 05, [mfgCode: "0x1185"]) // SETPOINT MODE    
    sendZigbeeCommands(cmds)   
}

def resetPower() {
    state.startTimestamp = now();
    state.lastPowerTimestamp = now();
    state.lastPower = 0.0;
    state.energy = 0.0;
    state.totalRunTime = 0.0;
}

def deviceNotification(text) {
    trace("deviceNotification(${text})", "debug")
    double outdoorTemp = text.toDouble()
    def cmds = []

    if (prefDisplayOutdoorTemp) {
        trace("deviceNotification() : Received outdoor weather : ${text} : ${outdoorTemp}", "debug")
    
        //the value sent to the thermostat must be in C
        if (getTemperatureScale() == 'F') {    
            outdoorTemp = fahrenheitToCelsius(outdoorTemp).toDouble()
        }        
        
        int outdoorTempDevice = outdoorTemp*100
        cmds += zigbee.writeAttribute(0xFF01, 0x0011, 0x21, 10800)   //set the outdoor temperature timeout to 3 hours
        cmds += zigbee.writeAttribute(0xFF01, 0x0010, 0x29, outdoorTempDevice, [mfgCode: "0x119C"]) //set the outdoor temperature as integer
        sendZigbeeCommands(cmds)
    } else {
        trace("deviceNotification() : Not setting any outdoor weather, since feature is disabled.", "debug")
    }
}

/////////////////////////////////////////////////////////////////////////////
// Unused
/////////////////////////////////////////////////////////////////////////////

def fanAuto() {}
def fanCirculate() {}
def fanOn() {}
def setCoolingSetpoint(degrees) {}
def setSchedule(JSON_OBJECT){}
def setThermostatFanMode(fanmode){}

/////////////////////////////////////////////////////////////////////////////
// private
/////////////////////////////////////////////////////////////////////////////

def computeEnergy(newPower) {
    def lastPowerTimestamp = state.lastPowerTimestamp;
    def lastPower = state.lastPower;
    state.lastPowerTimestamp = now();
    state.lastPower = newPower;
    def h = ( state.lastPowerTimestamp - lastPowerTimestamp ) / 1000.0 / 3600.0;  
    state.energy += ((state.lastPower + lastPower) / 2.0) * (h);
    
    def elapsed = (now() - state.startTimestamp) / 1000.0 / 60.0 / 60.0 / 24.0;
    state.energyDuration = "${elapsed.setScale(2, BigDecimal.ROUND_HALF_UP)} Days";
    if (newPower > 0) {
        state.totalRunTime += (state.lastPowerTimestamp - lastPowerTimestamp)/1000.0/60.0; 
    }
}

def sendZigbeeCommands(cmds) {
    cmds.removeAll { it.startsWith("delay") }
    def hubAction = new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE)
    sendHubCommand(hubAction)
}

def getTemperature(value) {
    if (value != null) {
        def celsius = Integer.parseInt(value, 16) / 100
        if (getTemperatureScale() == "C") {
            return celsius
        }
        else {
            return Math.round(celsiusToFahrenheit(celsius))
        }
    }
}

def getModeMap() {
  [
    "00": "off",
    "04": "heat"
  ]
}

def getLockMap() {
  [
    "00": "unlocked ",
    "01": "locked ",
  ]
}

def getActivePower(value) {
  if (value != null) {
    def activePower = Integer.parseInt(value, 16)
    return activePower
  }
}

def getTemperatureScale() {
    return "${location.temperatureScale}"
}

def getHeatingDemand(value) {
    if (value != null) {
        def demand = Integer.parseInt(value, 16)
        return demand
    }
}

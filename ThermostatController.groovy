definition(
    name: "ThermostatController",
    namespace: "hubitat",
    author: "David Hebert",
    description: "",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Application name", submitOnChange: true, required: true
            if(thisName) app.updateLabel("$thisName")
        }
        section("Setup") {
            input "thermostat", "capability.thermostat", title: "Thermostat"
            input "contactSensors", "capability.contactSensor", multiple: true, title: "Contact sensors"
            input "motionSensors", "capability.motionSensor", multiple: true, title: "Motion sensors"
            input "motionSwitches", "capability.switch", multiple: true, title: "Motion sensors (virtual switch)"
            input "coolingDevices", "capability.switch", multiple: true, title: "Fans"
            input "coolingPoint", "number", required: true, title: "Fans temp"
            input "debugEnabled", "bool", required: true, title: "Enabling debug logging"
        }
	}
}

/////////////////////////////////////////////////////////////////////////////
// System
/////////////////////////////////////////////////////////////////////////////

def installed() {
	initialize()
}

def updated() {
    initialize()
}

def rebooted(evt) {
    initialize(); 
}

def initialize() {
	trace("initialize", false);
    unsubscribe();
    unschedule();
    subscribe(contactSensors, "contact", checkStatus);
    subscribe(motionSensors, "motion", checkStatus);
    subscribe(motionSwitches, "switch", checkStatus);
    subscribe(location, "systemStart", rebooted);
    checkStatus();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def checkStatus(evt) {
    state.mode = thermostat.currentValue("thermostatMode");
    state.temperature = thermostat.currentValue("temperature");
    state.contact = false;
    state.motion = false;
    state.cooling = false;

    contactSensors.each { device ->
        if ( device.currentValue("contact") == "open" ) {
            state.contact = true;
        }
    }    
    motionSensors.each { device ->
        if ( device.currentValue("motion") == "active" ) {
            state.motion = true;
        }
    }    
    motionSwitches.each { device ->
        if ( device.currentValue("switch") == "on" ) {
            state.motion = true;
        }
    }
    coolingDevices.each { device ->
        if ( device.currentValue("switch") == "on" ) {
            state.cooling = true;
        }
    }         

    trace("CheckStatus: Thermostat is ${state.mode}, Contact is ${state.contact}, Motion is ${state.motion}, Temperature is ${state.temperature}, Cooling is ${state.cooling}", true);

    if (state.contact == true) {
        // windows/door are opened
        if (state.mode == "heat") {
            trace("Turning off the thermostat", false);
            thermostat.off();
        }
    } else {
        // windows/door are closed
        if (state.mode != "heat") {
            trace("Turning on the thermostat", false);
            thermostat.heat();
        }
    }

    if (state.temperature > coolingPoint && state.cooling == false) {
        trace("Turning on the fans", true);
        coolingDevices.each { device ->
            device.on();
        }  
    } else if ((state.temperature + 2) < coolingPoint && state.cooling == true) {
        trace("Turning off the fans", true);
        coolingDevices.each { device ->
            device.off();
        }  
    }
}

/////////////////////////////////////////////////////////////////////////////
// Common
/////////////////////////////////////////////////////////////////////////////

def isExpired(timestamp, delay) {
    def elapsed = now() - timestamp
    if ( elapsed > ( delay * 1000 ) ) {
        return true;
    }
    return false;
}

def trace(message, debug) {
    if (debug == true) {
        if (debugEnabled == true) { 
            log.debug message
        }        
    } else {
        log.info message
    }
}

def traceError(msg){
	log.error msg
}
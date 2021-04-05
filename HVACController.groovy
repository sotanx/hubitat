definition(
    name: "HVACController",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Control your HVAC",
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
            input "thermostat", "capability.thermostat", required: true, title: "Thermostat"
            input "sensors", "capability.contactSensor", required: false, multiple: true, title: "Door/window sensors"
            input "modeDelay", "number", required: true, title: "Set mode delay"
            input "awayDelay", "number", required: true, title: "Set away delay"
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
    subscribe(sensors, "contact", checkStatus);
    subscribe(location, "systemStart", rebooted);
    state.waitingMode = false;
    state.previousMode = "unknown";
    checkStatus();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def checkStatus(evt) {
    state.mode = thermostat.currentValue("thermostatMode");
    state.contact = false; // normal state

    sensors.each { device ->
        if ( device.currentValue("contact") == "open" ) {
            state.contact = true;
        }
    }    

    trace("CheckStatus: Mode ${state.mode}. Contact sensors opened ${state.contact}", true);
    checkContact();
}

def checkContact() {
    if ( state.contact == true ) {
        // contact are opened
        if ( state.mode != "off") {
            if (state.waitingMode == true) {
                if ( isExpired(state.waitingModeTime, modeDelay) ) {
                    trace("Turning off the HVAC (current: ${state.mode})", false);
                    state.previousMode = state.mode;
                    thermostat.off();
                }
            } else {
                trace("Waiting before turning off the system...", true);
                state.waitingMode = true;
                state.waitingModeTime = now()
                runIn(modeDelay, checkStatus);        
            }
        }
    } else {
        // contact are closed
        state.waitingMode = false;
        if ( state.mode == "off") {
            if ( state.previousMode == "heat" ) {
                trace("Restoring HVAC to heat mode", false);
                thermostat.heat();
            } else if ( state.previousMode == "cool" ) {
                trace("Restoring HVAC to cool mode", false);
                thermostat.cool();
            } else if ( state.previousMode == "auto" ) {
                trace("Restoring HVAC to auto mode", false);
                thermostat.auto();
            }
            state.previousMode = "unknown";
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
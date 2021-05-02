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
            input "weather", "capability.sensor", required: false, title: "Weather forecast device" 
            input "weatherThreshold", "number", required: true, title: "Forecast threshold temperature", defaultValue: 12
            input "minTemp", "number", required: true, title: "Temperature threshold to switch to mode", defaultValue: 18
            input "modeDelay", "number", required: true, title: "Set mode delay"
            input "awayDelay", "number", required: true, title: "Set away delay"
            input "debugEnabled", "bool", required: true, title: "Enable debug logging", defaultValue: false
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
	trace("initialize", "info");
    unsubscribe();
    unschedule();
    subscribe(sensors, "contact", checkStatus);
    subscribe(location, "systemStart", rebooted);
    state.waitingMode = false;
    state.previousMode = "unknown";
    checkStatus();
    checkForecast();
    runEvery3Hours(checkForecast);
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def checkForecast() {
    if ( weather != null ) {
        state.mode = thermostat.currentValue("thermostatMode");
        state.temperature = thermostat.currentValue("temperature");
        state.forecast = weather.currentValue("forecastHigh");
        if ( state.mode != "off" ) {
            // only change if the system isn't currently turned off
            if ( state.forecast >= weatherThreshold ) {
                if (state.mode != "cool") {
                    // house must be warm enough already
                    if (state.temperature > minTemp ) {
                        trace("Setting HVAC to cool mode (forecast)", "info");
                        thermostat.cool();
                    }
                }
            } else {
                if (state.mode != "heat") {
                    if (state.temperature < minTemp ) {
                        trace("Setting HVAC to heat mode (forecast)", "info");
                        thermostat.heat();
                    }
                }
            }
        }
    }
}

def checkStatus(evt) {
    state.mode = thermostat.currentValue("thermostatMode");
    state.contact = false; // normal state

    sensors.each { device ->
        if ( device.currentValue("contact") == "open" ) {
            state.contact = true;
        }
    }    

    trace("Mode ${state.mode}. Contact sensors opened ${state.contact}", "debug");
    checkContact();
}

def checkContact() {
    if ( state.contact == true ) {
        // contact are opened
        if ( state.mode != "off") {
            if (state.waitingMode == true) {
                if ( isExpired(state.waitingModeTime, modeDelay) ) {
                    trace("Turning off the HVAC (current: ${state.mode})", "info");
                    state.previousMode = state.mode;
                    thermostat.off();
                }
            } else {
                trace("Waiting before turning off the system...", "debug");
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
                trace("Restoring HVAC to heat mode", "info");
                thermostat.heat();
            } else if ( state.previousMode == "cool" ) {
                trace("Restoring HVAC to cool mode", "info");
                thermostat.cool();
            } else if ( state.previousMode == "auto" ) {
                trace("Restoring HVAC to auto mode", "info");
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

def trace(message, level) {
    def output = "[${thisName}] ${message}";
    if (level == "debug") {
        if (debugEnabled == true) { 
            log.debug output
        }        
    } else if (level == "info") {
        log.info output
    } else if (level == "error") {
        log.error output
    }
}
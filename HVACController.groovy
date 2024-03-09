#include helperLib.utils

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
            input "weatherThreshold", "number", required: true, title: "Forecast threshold temperature", defaultValue: 15
            input "maxTemp", "number", required: true, title: "Max temperature to switch to cool mode", defaultValue: 21
            input "minTemp", "number", required: true, title: "Min temperature to switch to heat mode", defaultValue: 20
            input "modeDelay", "number", required: true, title: "Set mode delay"
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
    checkStatus();
    runEvery1Hour(setThermostatMode);
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def setThermostatMode() {
    state.contact = false; // normal state
    sensors.each { device ->
        if ( device.currentValue("contact") == "open" ) {
            state.contact = true;
        }
    }

    if ( weather != null && state.contact == false) {
        state.mode = thermostat.currentValue("thermostatMode");
        state.temperature = thermostat.currentValue("temperature");
        state.forecast = weather.currentValue("forecastHigh");
        trace("checkThermostatMode Mode = ${state.mode}, Current temp = ${state.temperature}, ForecastHigh = ${state.forecast}", "debug");
        if ( state.forecast >= weatherThreshold ) {
            if (state.mode != "cool") {
                if (state.mode == "off" || state.temperature > maxTemp ) {
                    // only switch if the house is warm enough
                    trace("Setting HVAC to cool mode", "info");
                    thermostat.cool();
                } else {
                    trace("ThermostatMode didn't switch 'cool' since it's too cold already", "debug");
                }
            } else {
                trace("ThermostatMode already 'cool'", "debug");
            }
        } else {
            if (state.mode != "heat") {
                if (state.mode == "off" || state.temperature < minTemp ) {
                    trace("Setting HVAC to heat mode", "info");
                    thermostat.heat();
                } else {
                    trace("ThermostatMode didn't switch 'heat' since it's too warn already", "debug");
                }
            } else {
                trace("ThermostatMode already 'heat'", "debug");
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

    if ( state.contact == true ) {
        // contact are opened
        if ( state.mode != "off") {
            if (state.waitingMode == true) {
                if ( isExpired(state.waitingModeTime, modeDelay) ) {
                    trace("Turning off the HVAC (current: ${state.mode})", "info");
                    thermostat.off();
                }
            } else {
                trace("Waiting before turning off the system...", "debug");
                state.waitingModeTime = now();
                state.waitingMode = true;
                runIn(modeDelay, checkStatus);        
            }
        }
    } else {
        // contact are closed
        state.waitingMode = false;
        setThermostatMode();
    }
}

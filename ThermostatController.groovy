#include helperLib.utils

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
            input "motionTimeout", "number", required: true, title: "Motion timeout delay (seconds)"
            input "maxTemperature", "number", required: true, title: "Max temperature that can be set"
            input "minTemperature", "number", required: true, title: "Min temperature that can be set"
            input "deltaEcoTemperature", "number", required: true, title: "delta temp when eco mode"
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
    subscribe(contactSensors, "contact", checkStatus);
    subscribe(motionSensors, "motion", checkStatus);
    subscribe(motionSwitches, "switch", checkStatus);
    subscribe(thermostat, "thermostat", checkStatus);
    subscribe(location, "systemStart", rebooted);
    subscribe(location, "mode", checkStatus)
    state.lastMotion = now();
    state.ecoMode = false;
    state.vacationMode = false;
    state.normalTemperature = 19.0;
    checkTemperature();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def checkTemperature() {
    checkStatus();
    runIn(30, checkTemperature)
}

def checkStatus(evt) {
    // Gather all states
    state.mode = thermostat.currentValue("thermostatMode");
    state.temperature = thermostat.currentValue("temperature");
    state.currentHeatingpoint = thermostat.currentValue("heatingSetpoint");
    state.contact = false;
    state.motion = false;
    state.cooling = false;

    contactSensors.each { device ->
        if ( device.currentValue("contact") == "open" ) {
            state.contact = true;
        }
    }    
    
    def totalMSSensors = 0;
    if (motionSensors != null) {
        totalMSSensors += motionSensors.size();
        motionSensors.each { device ->
            if ( device.currentValue("motion") == "active" ) {
                state.motion = true;
            }
        }    
    }
    if (motionSwitches != null) {
        totalMSSensors += motionSwitches.size();
        motionSwitches.each { device ->
            if ( device.currentValue("switch") == "on" ) {
                state.motion = true;
            }
        }
    }
    if (totalMSSensors == 0) {
        state.motion = true; // no ms sensor... consider there's always movement to prevent lowering temperature
    }

    coolingDevices.each { device ->
        if ( device.currentValue("switch") == "on" ) {
            state.cooling = true;
        }
    }         

    trace("Thermostat ${state.mode}, Contact ${state.contact}, Motion ${state.motion}, Temperature ${state.temperature}, Cooling ${state.cooling}, EcoMode ${state.ecoMode}, VacationMode ${state.vacationMode}", "debug");

    // Exe state machine
    if ( location.getMode() == "Vacation" ) {
        if ( state.vacationMode == false ) {
            state.vacationMode = true;
            setEcoMode();
            // turn off fans
            coolingDevices.each { device ->
                device.off();
            }
        }
    } else {
        // normal operation
        if (state.vacationMode == true) {
            // Exiting vacation mode. Fake motion so that system will go back to normal
            state.vacationMode = false;
            state.motion = true;
        }
        handleMotion();
        handleContact();
        handleCooling();
    }
}

def handleMotion() {
    if (state.motion == true) {
        state.lastMotion = now();
        if ( state.ecoMode == true ) {
            setNormalMode();
        }
    } else {
        // no motion timeout?
        if ( isExpired(state.lastMotion, motionTimeout) == true && state.ecoMode == false ) {
            setEcoMode();
        }
    }
}

def setEcoMode() {
    if (state.ecoMode == false) {
        state.ecoMode = true;
        state.normalTemperature = state.currentHeatingpoint;
    }
    def newTemp = state.normalTemperature - deltaEcoTemperature;
    if ( location.getMode() == 'Night' ) {
        // don't lower it that much during the night
        newTemp = state.normalTemperature - (deltaEcoTemperature / 2);
    }
    if (state.currentHeatingpoint > newTemp) {
        trace("Setting eco mode...", "info");
        setTemperature(newTemp);
    }
}

def setNormalMode() {
    state.ecoMode = false;
    if (state.currentHeatingpoint < state.normalTemperature) {
        trace("Restoring temperature to normal", "info");
        setTemperature(state.normalTemperature);
    }
}

def setTemperature(newTemp) {
    if (newTemp > maxTemperature) {
        newTemp = maxTemperature;
    }
    if (newTemp < minTemperature) {
        newTemp = minTemperature;
    }
    trace("Setting temperature to ${newTemp}", "info");
    thermostat.setHeatingSetpoint(newTemp);
}

def handleContact() {
    if (state.contact == true) {
        // windows/door are opened
        if (state.mode == "heat") {
            trace("Turning off the thermostat", "info");
            thermostat.off();
        }
    } else {
        // windows/door are closed
        if (state.mode != "heat") {
            trace("Turning on the thermostat", "info");
            thermostat.heat();
        }
    }
}

def handleCooling() {
    if (state.temperature > coolingPoint && state.cooling == false) {
        trace("Turning on the fans", "info");
        coolingDevices.each { device ->
            device.on();
        }  
    } else if ((state.temperature + 2) < coolingPoint && state.cooling == true) {
        trace("Turning off the fans", "info");
        coolingDevices.each { device ->
            device.off();
        }  
    }
}

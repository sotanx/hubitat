#include helperLib.utils

definition(
    name: "Device Power Meter",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Detect if the device is currently used or not ( integration for Alexa )",
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
            input "pollRate", "number", required: true, title: "Cycle rate (seconds)"
            input "powerThreshold", "number", required: true, title: "Minimum power threshold (watt)"
            input "started", "number", required: true, title: "Started after X cycle where power > threshold"
            input "stopped", "number", required: true, title: "Stopped after X cycle where power < threshold"
            input "debugEnabled", "bool", required: true, title: "Enable debug logging", defaultValue: false
        }
        section {        
			input "powerSensor", "capability.powerMeter", title: "Select power meter", submitOnChange: true, required: true, multiple: false
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

def uninstalled() {
    trace("uninstalled", "info");
	unschedule();
	removeChildDevices(getChildDevices());
}

def initialize() {
    trace("initialize", "info");
    unsubscribe()
    unschedule();

    // Create master switch device
    masterContact = getChildDevice("Contact_${app.id}")
	if(!masterContact)
    {
        trace("add device Contact_${app.id}", "info");
        addChildDevice("hubitat", "Virtual Contact Sensor", "Contact_${app.id}", null, [label: thisName, name: thisName])
        masterContact = getChildDevice("Contact_${app.id}")
    }

    // Subscribe
    subscribe(location, "systemStart", rebooted)
   
    // Start!
    state.startTimestamp = now();
    state.lastPowerTimestamp = now();
    state.lastPower = 0.0;
    state.energy = 0.0;
    state.totalRunTime = 0.0;
    state.active = false;
    state.tick = 0;
    state.virtualContact = masterContact.displayName;
    checkStatus()
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def checkStatus() {
    // Read status
    currentPower = powerSensor.currentValue("power");
    computeEnergy(currentPower);

    if ( state.active == true ) {
        // Currently active... waiting for off
        if ( currentPower < powerThreshold ) {
            state.tick = state.tick + 1;
            if ( state.tick >= stopped ) {
                state.tick = 0
                state.active = false;
                masterContact = getChildDevice("Contact_${app.id}")
                masterContact.close()
                trace("Contact closed", "info");
            }
        } else {
            state.tick = 0
        }
    } else {
        // Currently inactive... waiting for on
        if ( currentPower >= powerThreshold ) {
            state.tick = state.tick + 1;
            if ( state.tick >= started ) {
                state.tick = 0
                state.active = true;
                masterContact = getChildDevice("Contact_${app.id}")
                masterContact.open()
                trace("Contact opened", "info");
            }
        } else {
            state.tick = 0        
        }
    }
    
    trace("State = ${state.active}, cycle = ${state.tick}, power = ${currentPower}", "debug");
    runIn(pollRate, checkStatus)
}

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

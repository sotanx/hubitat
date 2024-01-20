#include helperLib.utils

definition(
    name: "SunriseSunsetLight",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Automatically turn on/off lights with sunrise/sunset",
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
            input "masterSwitch", "capability.switch", title: "Master switch sunrise/sunret"
            input "motionSensors", "capability.motionSensor", multiple: true, title: "Motion sensors"
            input "motionSwitches", "capability.switch", multiple: true, title: "Motion sensors (virtual switch)"
            input "lightDelay", "number", required: true, title: "Turning off light delay (seconds)"
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
    subscribe(masterSwitch, "switch", checkStatus);
    subscribe(motionSwitches, "switch", checkStatus);
    subscribe(motionSensors, "motion", checkStatus);
    subscribe(location, "mode", checkStatus)
    state.lastMotion = currentDateTime().getTime();
    runEvery5Minutes(checkStatus)
    checkStatus();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def setMasterSwitchState(isOpen, open) {
    if (isOpen != open) {
        if (open == true) {
            trace("Sunset mode", "info");
            masterSwitch.on();
        } else {
            trace("Sunrise mode", "info");
            masterSwitch.off();
        }
    }
}

def checkStatus(evt) {
    def movement = false;
    def isSwitchOpen = masterSwitch.currentValue("switch") == "on";
    def sunset = isSunset();
    def now = currentDateTime();

    //  do we have motion
    motionSensors.each { device ->
        if ( device.currentValue("motion") == "active" ) {
            movement = true;
        }
    }    

    motionSwitches.each { device ->
        if ( device.currentValue("switch") == "on" ) {
            movement = true;
        }
    } 

    if (movement == true) {
        trace("Movement", "debug");
        state.lastMotion = currentDateTime().getTime();
    }

    def elapsed = getElapsedSeconds(state.lastMotion.toLong());
    trace("IsSunset=${sunset}, Switch ${isSwitchOpen}, Last movement ${elapsed} sec ago, Now ${now.toString()}", "debug");

    // sunset
    if ( sunset == true ) {
        if (isSwitchOpen == false) {
            if ( elapsed < lightDelay ) {
                // movement detected... turn it on
                if (now.hours <= 23) { // extra condition to prevent "late night snackers from opening this light..."
                    setMasterSwitchState(isSwitchOpen, true);
                }
            }
        } else {
            if ( elapsed > lightDelay ) {
                // no movement for the delay... turn it off
                setMasterSwitchState(isSwitchOpen, false);
            }
        }
    } else {
        // sunrise, no matter what... close the lights!
        setMasterSwitchState(isSwitchOpen, false);
    }

    // recheck after the light delay
    runIn(lightDelay, checkStatus);
}



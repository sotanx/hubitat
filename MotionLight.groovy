definition(
    name: "MotionLight",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Open/close light on movement and sunrise/sunset",
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
            input "light", "capability.switch", required: true, title: "Light switch"
            input "motionSensors", "capability.motionSensor", multiple: true, title: "Motion sensors"
            input "motionSwitches", "capability.switch", multiple: true, title: "Motion sensors (virtual switch)"
            input "presenceSensors", "capability.contactSensor", multiple: true, title: "Presence sensors"
            input "lightLevel", "number", required: true, title: "Light level (0-99)"
            input "lightDelay", "number", required: true, title: "Turning off light delay (seconds)"
            input "suspendDelay", "number", required: true, title: "Manual suspend delay (seconds)"
            input "checkSunset", "bool", required: true, title: "Active on sunset only"
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
    subscribe(motionSwitches, "switch", checkStatus);
    subscribe(motionSensors, "motion", checkStatus);
    subscribe(presenceSensors, "contact", checkStatus);
    subscribe(light, "switch", checkStatus);
    subscribe(light, "pushed", lightPushed);
    subscribe(location, "systemStart", rebooted);
    state.waitingLightDelay = false;
    state.suspended = false;
    checkStatus();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

def lightPushed(evt) {
    // manual user interaction
    if (evt.value == 2) {
        trace("Suspending automatic action", "info")
        state.suspended = true;
        state.suspendTime = now()
        runIn(suspendDelay, checkSuspend);        
    } else {
        state.suspended = false;
        trace("Removing suspended state", "info")
    }
}

def checkSuspend(evt) {
    if ( isExpired(state.suspendTime, suspendDelay) ) {
        trace("Suspend removed", "info");
        state.suspended = false;
        checkStatus();
    }
}

def checkStatus(evt) {
    checkLight();
}

def checkLight(evt) {
    state.light = light.currentValue("switch");
    state.movement = false;
    state.present = false;

    presenceSensors.each { device ->
        if ( device.currentValue("contact") == "open" ) {
            state.present = true;
        }
    }    
    
    motionSensors.each { device ->
        if ( device.currentValue("motion") == "active" ) {
            state.movement = true;
        }
    }    

    motionSwitches.each { device ->
        if ( device.currentValue("switch") == "on" ) {
            state.movement = true;
        }
    }    

    trace("Light ${state.light} Movement ${state.movement} Suspended ${state.suspended} Present ${state.present}", "debug");

    if (state.suspended == false) {
        if ( state.light == "off" ) {
            state.waitingLightDelay = false;
            if ( state.movement == true ) {
                if ( checkSunset == false || isSunset() == true ) {
                    trace("Turning on light to ${lightLevel}%", "info");
                    light.setLevel(lightLevel);
                }
            }
        } else {
            // light is on
            if ( state.movement == false ) {
                if (state.waitingLightDelay == false) {
                    state.waitingLightDelay = true;
                    state.delayLightTime = now()
                    runIn(lightDelay, checkLight);
                } else {
                    if ( isExpired(state.delayLightTime, lightDelay) ) {
                        if ( state.present == false ) {
                            trace("Turning off the light", "info");
                            light.off();
                        }
                    }
                }
            } else {
                state.waitingLightDelay = false;
            }
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Common
/////////////////////////////////////////////////////////////////////////////

def isExpired(timestamp, delay) {
    def elapsed = now() - timestamp;
    if ( elapsed > ( delay * 1000 ) ) {
        return true;
    }
    return false;
}

def isSunset() {
    def currTime = new Date();
    if (currTime > location.sunset || currTime < location.sunrise) {
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

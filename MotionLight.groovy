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
            input "lightLevel", "number", required: true, title: "Light level (0-99)"
            input "lightDelay", "number", required: true, title: "Turning off light delay (seconds)"
            input "suspendDelay", "number", required: true, title: "Manual suspend delay (seconds)"
            input "debugEnabled", "bool", required: true, title: "Enable debug logging"
        }
	}
}

def installed() {
	initialize()
}

def updated() {
    initialize()
}

def initialize() {
	trace("initialize", false);
    unsubscribe();
    unschedule();
    subscribe(motionSwitches, "switch", checkStatus);
    subscribe(motionSensors, "motion", checkStatus);
    subscribe(light, "switch", lightToggled);
    subscribe(location, "systemStart", rebooted);
    state.waitingLightDelay = false;
    state.suspended = false;
    state.systemAction = false;
    checkStatus();
}

// Private methods

def rebooted(evt) {
    trace("rebooted", false);
    initialize(); 
}

def lightToggled(evt) {
    if (state.systemAction == false ) {
        // manual user interaction
        state.suspended = true;
        state.suspendTime = now()
        trace("Suspending automatic action", false)
        runIn(suspendDelay, checkSuspend);        
    }
    state.systemAction = false;
    checkStatus();
}

def checkSuspend(evt) {
    trace("Suspend removed", false);
    state.suspended = false;
    checkStatus();
}

def checkStatus(evt) {
    checkLight();
}

def checkLight(evt) {
    state.light = light.currentValue("switch");
    state.movement = false;
    
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

    trace("CheckStatus: light is ${state.light} and movement is ${state.movement} Suspended ${state.suspended} (Mode ${location.getMode()})", true);

    if (state.suspended == false) {
        if ( state.light == "off" ) {
            state.waitingLightDelay = false;
            if ( state.movement == true ) {
                if (location.getMode() != "Day") {
                    trace("Turning on light to ${lightLevel}%", false);
                    state.systemAction = true;
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
                    def elapsed = now() - state.delayLightTime
                    if ( elapsed > ( lightDelay * 1000 ) ) {
                        trace("Turning off the light", false);
                        state.systemAction = true;
                        light.off();
                    }
                }
            } else {
                state.waitingLightDelay = false;
            }
        }
    }
}

// Common
def trace(message, debug) {
    if (debug == true) {
        if (debugEnabled == true) { 
            log.debug message
        }        
    } else {
        log.info message
    }
}
definition(
    name: "Auto door lock",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Make sure the door is locked!",
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
            input "door", "capability.lock", required: true, title: "Door lock"
            input "openSensor", "capability.contactSensor", required: true, title: "Door contact sensor"
            input "doorBellButton", "capability.switch", title: "Door bell button"
            input "doorLight", "capability.switch", title: "Door light"
            input "lockDelay", "number", required: true, title: "Locking delay (seconds)"
            input "lightDelay", "number", required: true, title: "Turning off light delay (seconds)"
            input "debugEnabled", "bool", required: true, title: "Enabling debug logging"
        }
	}
}

def installed() {
	initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
	trace("initialize", false);
    subscribe(door, "lock", checkStatus);
    subscribe(openSensor, "contact.closed", checkStatus);
    subscribe(openSensor, "contact.open", checkStatus);    
    subscribe(doorBellButton, "switch.off", checkStatus);
    subscribe(doorBellButton, "switch.on", checkStatus);
    subscribe(doorLight, "switch.on", checkStatus);
    subscribe(location, "systemStart", rebooted);
    state.waitingLockDelay = false;
    state.waitingLightDelay = false;
    safetyCheck();
    checkStatus();
}

// Private methods

// after a reboot, the door lock state might be wrong... explicitly make sure the door is locked
def rebooted(evt) {
    trace("rebooted", false);
    door.lock();
    initialize(); 
}

// Make sure the door is lock... no matter what!
def safetyCheck(evt) {
    trace("safetyCheck", true);
    if ( openSensor.currentValue("contact") == "closed" ) {
        if ( door.currentValue("lock") == "unlocked" ) {
            trace("Safety: Locking the door!", false);
            door.lock();
        }
    }
    runIn(300, safetyCheck);
}

def checkStatus(evt) {
    trace("CheckStatus: Door is ${door.currentValue("lock")} and ${openSensor.currentValue("contact")}. Light is ${doorLight.currentValue("switch")} (Mode ${location.getMode()})", true);
    checkLock();
    checkLight();
}

// Lock logic
def checkLock() {
    if ( door.currentValue("lock") == "locked" ) {
        state.waitingLockDelay = false;
    } else {
        if ( openSensor.currentValue("contact") == "open" ) {
            state.waitingLockDelay = false;
            trace("door is still opened.. waiting for events", true);
        } else {
            if ( state.waitingLockDelay == false ) {
                state.waitingLockDelay = true;
                state.delayLockTime = now()
                trace("Waiting for the delay to lock the door", true)
                runIn(lockDelay, checkLock);
            } else {
                def elapsed = now() - state.delayLockTime
                if ( elapsed > ( lockDelay * 1000 ) ) {
                    trace("Locking the door!", false);
                    door.lock();
                }
            }
        }
    }
}

// Light logic
def checkLight() {
    if ( doorLight.currentValue("switch") == "off" ) {
        state.waitingLightDelay = false;
        if ( ( openSensor.currentValue("contact") == "open" ) || ( doorBellButton.currentValue("switch") == "on" ) ) {
            // either the door is open or the button was pressed
            if (location.getMode() != "Day") {
                trace("Turning on the light (${location.getMode()})", false);
                doorLight.on();
            }
        }
    } else {
        if ( ( openSensor.currentValue("contact") == "closed" ) && ( doorBellButton.currentValue("switch") == "off" ) ) {
            // door is closed
            if (state.waitingLightDelay == false) {
                state.waitingLightDelay = true;
                state.delayLightTime = now()
                runIn(lightDelay, checkLight);
            } else {
                def elapsed = now() - state.delayLightTime
                if ( elapsed > ( lightDelay * 1000 ) ) {
                    trace("Turning off the light", false);
                    doorLight.off();
                }
            }
        } else {
            state.waitingLightDelay = false;
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
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
            input "doorSuspend", "capability.switch", title: "Suspend locking door switch"
            input "lockDelay", "number", required: true, title: "Locking delay (seconds)"
            input "lightDelay", "number", required: true, title: "Turning off light delay (seconds)"
            input "suspendDelay", "number", required: true, title: "Suspend delay (seconds)"
            input "debugEnabled", "bool", required: true, title: "Enabling debug logging"
        }
	}
}

/////////////////////////////////////////////////////////////////////////////
// System
/////////////////////////////////////////////////////////////////////////////

def installed() {
	initialize();
}

def updated() {
    initialize();
}

def rebooted(evt) {
    // after a reboot, the door lock state might be wrong... explicitly make sure the door is locked
    trace("rebooted", false);
    door.lock();
    initialize(); 
}

def initialize() {
	trace("initialize", false);
    unsubscribe();
    unschedule();
    subscribe(door, "lock", checkStatus);
    subscribe(openSensor, "contact", checkStatus);    
    subscribe(doorBellButton, "switch", checkStatus);
    subscribe(doorLight, "switch", checkStatus);
    subscribe(doorSuspend, "switch", checkStatus);
    subscribe(location, "systemStart", rebooted);
    state.waitingLockDelay = false;
    state.waitingLightDelay = false;
    state.suspended = false;
    safetyCheck();
    checkStatus();
}

/////////////////////////////////////////////////////////////////////////////
// Private
/////////////////////////////////////////////////////////////////////////////

// Make sure the door is lock... no matter what!
def safetyCheck(evt) {
    trace("safetyCheck", true);
    if ( doorSuspend.currentValue("switch") == "off" ) {
        if ( openSensor.currentValue("contact") == "closed" ) {
            if ( door.currentValue("lock") == "unlocked" ) {
                trace("Safety: Locking the door!", false);
                door.lock();
            }
        }
    }
    runIn(300, safetyCheck);
}

def checkStatus(evt) {
    trace("CheckStatus: Door is ${door.currentValue("lock")} and ${openSensor.currentValue("contact")}. Light is ${doorLight.currentValue("switch")} (Mode ${location.getMode()}) Suspended ${doorSuspend.currentValue("switch")}", true);
    checkSuspend();
    checkLock();
    checkLight();
}

// Suspend logic
def checkSuspend() {
    if ( doorSuspend.currentValue("switch") == "on" ) {
        if ( state.suspended == true ) {
            if ( isExpired(state.suspendTime, suspendDelay) ) {
                trace("Removing suspend mode", false);
                doorSuspend.off();
            }
        } else {
            state.suspended = true;
            door.unlock();
            state.suspendTime = now()
            trace("Waiting for the delay to remove the suspend", true)
            runIn(suspendDelay, checkSuspend);
        }
    } else {
        state.suspended = false;
    }
}

// Lock logic
def checkLock() {
    if ( door.currentValue("lock") == "locked" ) {
        // locked
        state.waitingLockDelay = false;
    } else {
        // unlocked
        if ( openSensor.currentValue("contact") == "open" ) {
            // opened
            state.waitingLockDelay = false;
        } else {
            // closed
            if ( state.waitingLockDelay == false ) {
                state.waitingLockDelay = true;
                state.delayLockTime = now()
                runIn(lockDelay, checkLock);
            } else {
                if ( isExpired(state.delayLockTime, lockDelay) ) {
                    if ( state.suspended == false ) {
                        trace("Locking the door!", false);
                        door.lock();
                    }
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
                if ( isExpired(state.delayLightTime, lightDelay) ) {
                    trace("Turning off the light", false);
                    doorLight.off();
                }
            }
        } else {
            state.waitingLightDelay = false;
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
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
            input "door", "capability.lock", title: "Door lock"
            input "openSensor", "capability.contactSensor", title: "Door contact sensor"
            input "lockDelay", "number", required: true, title: "Locking delay (seconds)"
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
	log.info "initialize"
    subscribe(door, "lock", checkStatus)
    subscribe(openSensor, "contact.closed", checkStatus)
    subscribe(openSensor, "contact.open", checkStatus)    
    subscribe(location, "systemStart", rebooted)
    state.doorLocked = false;
    state.doorIsOpen = false;
    state.waitingDelay = false;
    checkStatus()
}

// Private methods

def rebooted(evt) {
    door.lock(); // after a reboot, the door lock state isn't correct, make sure the door is locked
}

def checkStatus(evt) {
    if ( door.currentValue("lock") == "locked" ) {
        state.doorLocked = true;
        state.waitingDelay = false;
        log.debug "Door is locked. Do nothing."
    } else {
        log.debug "door is unlocked"
        state.doorLocked = false;
        if ( openSensor.currentValue("contact") == "open" ) {
            state.doorIsOpen = true;
            state.waitingDelay = false;
            log.debug "door is still opened.. waiting for events"
        } else {
            state.doorIsOpen = false;
            if ( state.waitingDelay == false ) {
                state.waitingDelay = true;
                log.debug "Waiting for the delay to lock the door"
                runIn(lockDelay, checkStatus)            
            } else {
                log.debug "Locking the door!"
                door.lock();
            }
        }
    }
}

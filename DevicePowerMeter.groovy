definition(
    name: "Device Power Meter",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Detect if the device is currently used or not ( for integration for Alexa )",
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
        }
        section {        
			input "powerSensor", "capability.powerMeter", title: "Select power meter", submitOnChange: true, required: true, multiple: false
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

    // Create master switch device
    masterContact = getChildDevice("Contact_${app.id}")
	if(!masterContact)
    {
        addChildDevice("hubitat", "Virtual Contact Sensor", "Contact_${app.id}", null, [label: thisName, name: thisName])
    }
   
    // Start!
    state.active = false;
    state.tick = 0;
    checkStatus()
}

// Private methods

def checkStatus() {
    // log.info "checkStatus"
    
    // Read status
    currentPower = powerSensor.currentValue("power")
    
    if ( state.active == true ) {
        // Currently active... waiting for off
        if ( currentPower < powerThreshold ) {
            state.tick = state.tick + 1;
            if ( state.tick >= stopped ) {
                state.tick = 0
                state.active = false;
                masterContact = getChildDevice("Contact_${app.id}")
                masterContact.close()
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
            }
        } else {
            state.tick = 0        
        }
    }
    
    log.info "State = ${state.active}, cycle = ${state.tick}, power = ${currentPower}"
    runIn(pollRate, checkStatus)
}

definition(
    name: "Washer/Dryer detector",
    namespace: "hubitat",
    author: "David Hebert",
    description: "Washer/Dryer Running or not? Uses Aeon Multisensor 6 reset Tamper function",
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
            input "started", "number", required: true, title: "Started after X vibration cycle"
            input "stopped", "number", required: true, title: "Stopped after X no vibration cycle"
        }
        section {        
			input "accelerationSensor", "capability.accelerationSensor", title: "Select vibration Sensor", submitOnChange: true, required: true, multiple: false
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
    masterSwitch = getChildDevice("Switch_${app.id}")
	if(!masterSwitch)
    {
        addChildDevice("hubitat", "Virtual Switch", "Switch_${app.id}", null, [label: thisName, name: thisName])
    }
    
    // Subscribe
    // subscribe(accelerationSensor, "acceleration", accelerationHandler)
    
    // Start!
    state.active = false;
    state.tick = 0;
    checkStatus()
}

// Private methods

def checkStatus() {
    // log.info "checkStatus"
    
    // Is the sensor active or not?
    // First reset it
    accelerationSensor.resetTamperAlert()
    
    // Wait 5 sec
    pauseExecution(5000)    
    
    // Read status
    masterSwitch = getChildDevice("Switch_${app.id}")
    vibrating = false
    if ( accelerationSensor.currentValue("acceleration") == "active" ) {
        vibrating = true
    }
    
    if ( state.active == true ) {
        // Currently active... waiting for off
        if ( vibrating == false ) {
            state.tick = state.tick + 1;
            if ( state.tick >= stopped ) {
                state.tick = 0
                state.active = false;
                masterSwitch.off()
            }
        } else {
            state.tick = 0
        }
    } else {
        // Currently inactive... waiting for on
        if ( vibrating == true ) {
            state.tick = state.tick + 1;
            if ( state.tick >= started ) {
                state.tick = 0
                state.active = true;
                masterSwitch.on()
            }
        } else {
            state.tick = 0        
        }
    }
    
    log.info "State = ${state.active}, cycle = ${state.tick}"
    runIn(pollRate - 5, checkStatus)
}


// Event handlers

def accelerationHandler(evt) {
    log.info "accelerationHandler ${evt.value} on ${evt.displayName} (${evt.deviceId})"
}
